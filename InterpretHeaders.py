from typing import List, Union, Dict, Set, Tuple

import numpy as np
import pandas as pd
import torch
from numpy import mean
from torch import Tensor
from tqdm import tqdm

from BertSimilarityModel import BertSimilarityModel as Bert
from HeadersDataset import HeadersDataset
from MedicalDictionary import MedicalDictionary
from util.NearDuplicates import groupNearDuplicates, findNearDuplicates

pd.set_option('display.max_rows', None)
pd.set_option('display.max_columns', None)
pd.set_option('display.width', 1000)


class InterpretHeaders:

    def __init__(self, headers: List[str], medDictPath: str):

        self.hDataset = HeadersDataset(headers)
        self.medDict  = MedicalDictionary(dictionaryCSVPath=medDictPath,
                                          datasetAlphabet=self.hDataset.headersAlphabet)
        self.hRange = range(self.hDataset.__sizeof__())
        self.hContext = {}
        self._setup()

        print(">> Inter")
        self.bert = Bert()

        self.cachedEmbeddings: Dict[str, Tensor] = {}
        self._generateCachedEmbeddings()

        self.globalAbbrevScores: Dict[str, Dict[str,float]] = {}
        self.abbrevsCandsGroups: Dict[str, Dict[str, int]]  = {}
        self._setGlobalAbbrevInfo()

        self.headersCandsDFs: Dict[int, pd.DataFrame] = {}
        # print(self.hDataset.datasetAbbrevs)
        # [[print(f'{abbrev} -> {ff}  =  {score}') for ff, score in abbrevScores.items()] for abbrev, abbrevScores in self.globalAbbrevScores.items()]

        self._calcHeaderScores()
        self._findSeeds()
        self._setSeedScores()
        self._setGroups()

        self._printDataFrames()


    def _setup(self):
        for idx in self.hRange:
            self.hDataset.printHeaderInfo(idx)
            self.hDataset.setHeaderAbbrevsInfo(
                idx,
                * self.medDict.generateHeaderCandidates(self.hDataset.getHeaderInfo(idx))
            )
            print("=" * 30)
        self.hDataset.setDatasetAbbrevs(self.medDict.datasetAbbrevDetected)                                             # ;print(self.hDataset.datasetAbbrevs) ;[print(f"'{self.hDataset.tokenizedHeaders[idx]}',") for idx in self.hRange]

        for idx in self.hRange:
            if not self.hDataset.doesntContainAbbrevs(idx):
                self.hContext[idx] = self.hDataset.generateHeaderContext(idx)


    def _generateCachedEmbeddings(self):
        def _cacheCollectionEmbeddings(collection: Union[Set[str], List[str]]):
            collection = {el for el in collection if el not in self.cachedEmbeddings}
            batch, batchPos = Bert.createBatches(collection)
            embeddings = self.bert(batch)                                                                               ;print(f"Cached {len(collection)} new embeddings")
            for el in collection:
                pos = batchPos[el]
                self.cachedEmbeddings[el] = embeddings[pos[0]][pos[1]]

        _cacheCollectionEmbeddings(self.hDataset.datasetAbbrevs)
        _cacheCollectionEmbeddings(self.hDataset.tokenizedHeaders)                                                      # ; [print(k, v[:3]) for k,v in self.cachedEmbeddings.items()]



    def _setGlobalAbbrevInfo(self):

        for abbrev in self.hDataset.datasetAbbrevs:
            self.globalAbbrevScores[abbrev] = {}
            abbrevEmbedding = self.cachedEmbeddings[abbrev]

            fullForms = self.medDict.getExactMatch(abbrev)
            ffBatches, ffBatchPos = Bert.createBatches(fullForms)
            ffEmbeddings = self.bert(ffBatches)

            globalAbbrevScores = Bert.cos(abbrevEmbedding, ffEmbeddings)
            for fullForm in fullForms:
                pos = ffBatchPos[fullForm]
                self.globalAbbrevScores[abbrev][fullForm] = globalAbbrevScores[pos[0]][pos[1]].item()

        for i, (abbrev, ffs) in enumerate(self.globalAbbrevScores.items()):
            ffs = list(ffs.keys())
            _, nearDuplicates = findNearDuplicates(ffs, strict=False, leven_thrs=85)
            nearDuplicates = {ffs[i] : groupID for groupID, group in enumerate(nearDuplicates) for i in group}
            self.abbrevsCandsGroups[abbrev] = nearDuplicates



    def _calcHeaderScores(self):
        """
        LAD1 is possible whole header abbrev and contains partial abbrevs (LAD)
        header = LAD1, tokenized = LAD 1
        whole:
            wholeAbbrev: str = LAD1
            globalAbbrevScores[abbrev=LAD1]: Dict[str,double]
                wholeFF (key)      = full form of LAD1 e.g., leucocyte adhesion deficiency 1
                wholeScore (value) = score(LAD1, full form of LAD1)
                                   = cos(BERT(LAD1), BERT(leucocyte adhesion deficiency 1))

            wholeFFs: Set[str]
                The possible interpretations of LAD1 as retrieved from the Trie
                                {'leucocyte adhesion deficiency type 1', 'leucocyte adhesion deficiency 1'}
            wholeBatches: List[List[str]]
                The wholeFFs separated to batches
                            [['leucocyte adhesion deficiency type 1', 'leucocyte adhesion deficiency 1']]
            wholeBatchPos: Dict[str, Tuple[int, int]]
                For each whole ff, the batch and the position where it ended up
                            {'leucocyte adhesion deficiency type 1': (0, 0), 'leucocyte adhesion deficiency 1': (0, 1)}
            wholeEmbedings: List[Tensor]
                The embeddings of the wholeBatches
                    [[BERT('leucocyte adhesion deficiency type 1'), BERT('leucocyte adhesion deficiency 1')]]

        partial:
            partialAbbrevs: Tuple[str]
                The partial abbrevs within the header sorted according to span
                            (LAD,)
            partialCands: Dict[Tuple[str], str]
                For each combination of full forms of the partialAbbrevs within the header, the interpretation
                The key tuple corresponds with the partialAbbrevs tuple
                {
                    ('left anterior descending coronary artery',): 'left anterior descending coronary artery 1',
                    ('left anterior descending artery',): 'left anterior descending artery 1',
                    ('leucocyte adhesion deficiency',): 'leucocyte adhesion deficiency 1',
                }
            headerEmbedding: Tensor The cached embedding of LAD 1
            partialBatches: List[List[str]]
                Similar to wholeBatches, the interpretations of the header separated to batches
                    [['left anterior descending coronary artery 1', 'left anterior descending artery 1', 'leucocyte adhesion deficiency 1'],...]
            partialBatchPos: Dict[str, Tuple[int, int]]
                Similar to wholeBatchPos the batch and the position where each interpretation ended up
                    {'left anterior descending coronary artery 1' : (0,0), ...}
            partialEmbeddings: List[Tensor]
                The embeddings of partialBatches
                    [[BERT('left anterior descending coronary artery 1'), BERT('left anterior descending artery 1'), BERT('leucocyte adhesion deficiency 1')],...]
            partialScores: List[List[double]]
                = score(LAD 1, full form of LAD + 1)
                = cos(BERT(LAD 1), BERT(left anterior descending coronary artery 1))
                is in position partialScores[0][0]

            headerAbbrevsFFs: Tuple[str], headerFF: str
                The interpretation of each abbrev in the header and the interpretation of the header according to this combination
                    When abbrev:LAD is  headerAbbrevsFFs=left anterior descending coronary artery
                    the interpretation of tHeader:LAD 1 is headerFF='left anterior descending coronary artery 1'
            partialScore:
                LAD 1 * left anterior descending coronary artery 1

        """

        def _createHeaderCand(headerAbbrevsFFs_, headerFF_, isWholeHeader, score, group=None, contextScores=None,
                              globalScores=None):
            return {
                "headerAbbrevsFFs": headerAbbrevsFFs_,
                "headerFF": headerFF_,
                "isWholeHeader": isWholeHeader,
                "group": group,
                "score": score.item() if isinstance(score, torch.Tensor) else score,
                "meanCtxScore": torch.mean(torch.stack(contextScores), dim=0).item(),
                "globalScores": globalScores
            }

        for idx in tqdm(self.hRange):
            if not self.hDataset.doesntContainAbbrevs(idx):
                headerCands = []
                tHeader = self.hDataset.tokenizedHeaders[idx]                                                           # ;print(f"\nCalc for [{idx}] : {tHeader}")

                ctx = [self.hDataset.tokenizedHeaders[ctxIdx] for ctxIdx in self.hContext[idx]]
                ctxEmbeddings = [self.cachedEmbeddings[ctxHeader] for ctxHeader in ctx]
                # ======================================================================================================
                # The whole header is an abbreviation
                # ======================================================================================================
                if self.hDataset.isWholeHeaderAbbrev(idx):
                    wholeAbbrev = self.hDataset.headers[idx]                                                            # ;print(f"\tAbbrevs = Whole Header {wholeAbbrev}")

                    wholeFFs = list(self.globalAbbrevScores[wholeAbbrev].keys())
                    wholeBatches, wholeBatchPos = Bert.createBatches(wholeFFs)
                    wholeEmbeddings = self.bert(wholeBatches)

                    for wholeFF, wholeScore in self.globalAbbrevScores[wholeAbbrev].items():

                        pos  = wholeBatchPos[wholeFF]
                        emb  = wholeEmbeddings[pos[0]][pos[1]]
                        ctxScores = Bert.cos(emb, ctxEmbeddings)
                        headerCands.append(
                            _createHeaderCand(headerAbbrevsFFs_=(wholeFF,),
                                              headerFF_=wholeFF,
                                              isWholeHeader=True,
                                              score=wholeScore,
                                              contextScores=ctxScores,
                                              globalScores=(wholeScore,)))                                              # ;print(f"\t{wholeFF} : {wholeFF} = {wholeScore}")

                # ======================================================================================================
                # The header contains partial abbreviations
                # ======================================================================================================
                if self.hDataset.containsPartialAbbrevs(idx):

                    partialAbbrevs = self.hDataset.partialAbbrevsDetected[idx]
                    partialCands = self.hDataset.partialCands[idx]

                    headerEmbedding = self.cachedEmbeddings[tHeader]                                                    # ;print(f"\tPartial Abbrevs = {partialAbbrevs}\tEmb = {headerEmbedding}") # ;[print(headerAbbrevsFFs, headerFF) for headerAbbrevsFFs, headerFF in partialCands.items()]

                    partialBatches, partialBatchPos = Bert.createBatches(partialCands)
                    partialEmbeddings = self.bert(partialBatches)
                    partialScores = Bert.cos(headerEmbedding, partialEmbeddings)

                    for headerAbbrevsFFs, headerFF in partialCands.items():
                        pos = partialBatchPos[headerAbbrevsFFs]
                        partialScore = partialScores[pos[0]][pos[1]]
                                                                                                                        # ;print(f"\t{headerAbbrevsFFs} : {headerFF} = {partialScore}")
                        emb  = partialEmbeddings[pos[0]][pos[1]]
                        ctxScores = Bert.cos(emb, ctxEmbeddings)
                        headerCands.append(
                            _createHeaderCand(headerAbbrevsFFs_=headerAbbrevsFFs,
                                              headerFF_=headerFF,
                                              isWholeHeader=False,
                                              score=partialScore,
                                              contextScores=ctxScores,
                                              globalScores=tuple(self.globalAbbrevScores[abbrev][abbrevFF] for abbrev,abbrevFF in zip(partialAbbrevs,headerAbbrevsFFs))))

                headerCands = pd.DataFrame(headerCands)
                headerCands = self._firstRoundFiltering(idx, headerCands)
                self.headersCandsDFs[idx] = headerCands


    def _firstRoundFiltering(self, idx:int, headerCands:pd.DataFrame, FIRST_ROUND_THRS: float = 0.82):
        headerCands.sort_values(by='score', ascending=False, inplace=True, ignore_index=True)
        pheaderAbbrevs: Tuple = self.hDataset.partialAbbrevsDetected[idx]
        toRmv = set()
        for i, cand in headerCands.iterrows():
            if cand.score <= FIRST_ROUND_THRS:

                if cand.meanCtxScore <= FIRST_ROUND_THRS or (
                   cand.isWholeHeader and self.globalAbbrevScores[self.hDataset.headers[idx]][cand.headerFF] <= FIRST_ROUND_THRS):
                    toRmv.add(i)                                                                                        # ;print(f"\t\tRemove : {cand.headerFF}")
                else:
                    for hA, hAI in zip(pheaderAbbrevs, cand.headerAbbrevsFFs):
                        if self.globalAbbrevScores[hA][hAI] <= FIRST_ROUND_THRS:
                            toRmv.add(i)                                                                                # ;print(f"\t\tRemove : {cand.headerFF}")
                            break

        return headerCands.drop(toRmv, axis=0)



    def _findSeeds(self):
        def key(cand):
            return self.hDataset.headers[idx] if cand.isWholeHeader else pAbbrevs[0]

        self.seeds = {}
        for idx, cands in tqdm(self.headersCandsDFs.items()):
            if len(cands) == 0:
                continue

            # header = self.hDataset.headers[idx]
            pAbbrevs: Tuple = self.hDataset.partialAbbrevsDetected[idx]

            # complex headers are not considered seeds
            if not(len(pAbbrevs) == 1 or self.hDataset.isWholeHeaderAbbrev(idx)):
                continue

            # single high score candidate
            if len(cands) == 1:
                singleCand = cands.iloc[0]
                if singleCand.score > 0.85 and mean(singleCand.globalScores) > 0.85:
                    self.seeds[key(singleCand)] = singleCand.headerAbbrevsFFs[0]
                continue

            # single cand with scores greater than 90%
            isSeed = True
            top1Score, top1GlobalScore = cands.iloc[0].score, mean(cands.iloc[0].globalScores)
            if top1Score > 0.9 and top1GlobalScore > 0.9:
                for _, cand2 in cands.iloc[1:].iterrows():
                    if cand2.score > 0.9 and mean(cand2.globalScores) > 0.9:
                        isSeed = False
                        break
                if isSeed:
                    self.seeds[key(cands.iloc[0])] = cands.iloc[0].headerAbbrevsFFs[0]
                    continue


            # all top score cands are the same concept
            highScoreCands = []
            k = ''
            for _, cand in cands.iterrows():
                if cand.score >= 0.93 and mean(cand.globalScores) >= 0.93:
                    highScoreCands.append(cand.headerAbbrevsFFs[0])
                    k = self.hDataset.headers[idx] if cand.isWholeHeader else pAbbrevs[0]
            if len(highScoreCands) > 0:
                nearDuplicates = groupNearDuplicates(highScoreCands, strict= False, leven_thrs=80)
                if len(nearDuplicates) == 1:
                    self.seeds[k] = next(iter(nearDuplicates))

        [print(k,v) for k,v in self.seeds.items()]


    def _setSeedScores(self):
        seedsBatches, seedsBatchPos = Bert.createBatches(self.seeds)
        seedsEmbeddings = self.bert(seedsBatches)

        for idx, headerCands in tqdm(self.headersCandsDFs.items()):

            hAbbrevs = self.hDataset.getHeaderAbbrevs(idx)
            overlap = set()
            for cA in self.seeds.keys() & hAbbrevs:
                pos = seedsBatchPos[cA]
                overlap.add(pos)

            candsMeanSeedScores = []
            for _, cand in headerCands.iterrows():
                candEmbedding = self.bert([[cand.headerFF]])[0]
                seedScores = Bert.cos(candEmbedding, seedsEmbeddings)
                seedScores = [batch[jpos].item() for ib, batch in enumerate(seedScores) for jpos in range(batch.shape[0]) if (ib, jpos) not in overlap]
                candsMeanSeedScores.append(mean(seedScores))
            headerCands['meanSeedScore'] = candsMeanSeedScores


    def _weightAvgScores(self):
        candScoreW = 0.4
        globalW = 0.2
        ctxW = 0.3
        ssW = 0.3

        for idx, headerCands in self.headersCandsDFs.items():
            wAvgsScores = []
            for _, cand in headerCands.iterrows():
                meanGlobal = mean(cand.globalScores)
                wAvg = candScoreW * cand.score + globalW * meanGlobal + ctxW * cand.meanCtxScore + ssW * cand.meanSeedScores
                wAvgsScores.append(wAvg)
            headerCands['wAvg'] = wAvgsScores




# ======================================================================================================

    def custom_agg(self, series):
        # Check the data type of the series
        if np.issubdtype(series.dtype, np.number):
            return series.max()
        elif series.dtype == 'object' and all(isinstance(x, tuple) for x in series):
            if isinstance(series.iloc[0][0], str):
                return list(series)
            else:   # Get the maximum value per tuple position
                max_values = [max(item) for item in zip(*series)]
                return max_values
        elif series.dtype == 'object':
            return list(series)
        else:
            # Return the original series if the data type doesn't match any condition
            return series

    def _setGroups(self):
        """def calculate_group(cand):
            candAbbrevs = (self.hDataset.headers[idx],) if cand.isWholeHeader else self.hDataset.partialAbbrevsDetected[idx]
            candGroup = tuple(self.abbrevsCandsGroups[abbrev][abbrevFF] for abbrev,abbrevFF in zip(candAbbrevs,cand.headerAbbrevsFFs))
            return candGroup

        for idx, headerCands in self.headersCandsDFs.items():
            headerCands['group'] = headerCands.apply(calculate_group, axis=1)"""

        for idx, headerCands in self.headersCandsDFs.items():
            if len(headerCands) == 0:   # all cands were filtered
                continue
            for i, cand in headerCands.iterrows():
                candAbbrevs = (self.hDataset.headers[idx],) if cand.isWholeHeader else self.hDataset.partialAbbrevsDetected[idx]
                candGroup = tuple(self.abbrevsCandsGroups[abbrev][abbrevFF] for abbrev, abbrevFF in zip(candAbbrevs, cand.headerAbbrevsFFs))
                headerCands.at[i, 'group'] = candGroup

            self.headersCandsDFs[idx] = headerCands.groupby(['group', 'isWholeHeader']).agg(self.custom_agg).reset_index()
            self.headersCandsDFs[idx].sort_values(by='score', ascending=False, inplace=True, ignore_index=True)


    # ======================================================================================================

    def _printDataFrames(self):
        for idx, headerCands in self.headersCandsDFs.items():
            print(f"\n>> Header [{idx}]  {self.hDataset.tokenizedHeaders[idx]}\n")
            print(headerCands[['score', 'meanCtxScore', 'meanSeedScore', 'globalScores', 'group', 'headerFF']])









