import math
from typing import List, Union, Dict, Set, Tuple

from thefuzz import fuzz as fz

import torch
from numpy import mean
from torch import Tensor
from tqdm import tqdm

from HeadersDataset import HeadersDataset
from MedicalDictionary import MedicalDictionary
from BertSimilarityModel import BertSimilarityModel as Bert
from util.NearDuplicates import groupNearDuplicates


class HeaderCand:
    def __init__(self, headerAbbrevsFFs:Tuple[str], headerFF: str, score: Union[Tensor, float], isWholeHeader:bool):

        self.headerAbbrevsFFs= headerAbbrevsFFs
        self.headerFF = headerFF
        self.isWholeHeader = isWholeHeader

        self.score = score.item() if isinstance(score, Tensor) else score
        self.contextScores:List[Tensor] = []
        self.meanCtxScore: float = None
        self.seedScores: List = None
        self.meanSeedScores = None

        self.weightAvgScore = None


    def setContextScores(self, ctxScores):
        self.contextScores = ctxScores
        self.meanCtxScore = torch.mean(torch.stack(self.contextScores), dim=0).item()

    def setSeedScores(self, seedScores):
        self.seedScores = seedScores # torch.cat(seedScores, dim=0)
        self.meanSeedScores = mean(seedScores)

    def printCand(self, ctxHeaders, seeds=None):
        print(f"\t\tHFF =  '{self.headerFF}'\tIsWH = {self.isWholeHeader}\t Score =  {self.score}\n\t\tCtx Scores = ")
        for hctx, ctxScore in zip(ctxHeaders, self.contextScores):
            print(f"\t\t\t\tctx = {hctx}\t score = {ctxScore}")
        print("\t\tMean ctx score = ", self.meanCtxScore)

        if self.seedScores is not None:
            print("\t\tMean seed score = ", self.meanSeedScores, f"\t\t{self.seedScores}")
        if seeds is not None:
            pass
            # for seed, seedScore in zip(seeds, self.seedScores):
            #    print(f"\t\t\t\tseed = {seed}\t score = {seedScore}")


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
        self._calcGlobalAbbrevScores()
        # print(self.hDataset.datasetAbbrevs)
        # [[print(f'{abbrev} -> {ff}  =  {score}') for ff, score in abbrevScores.items()] for abbrev, abbrevScores in self.globalAbbrevScores.items()]

        self._calcHeaderScores()
        self._findSeeds()
        self._setSeedScores()
        self._weightAvgScores()
        self._printTable()


    def _setup(self):
        for idx in self.hRange:
            self.hDataset.printHeaderInfo(idx)
            self.hDataset.setHeaderAbbrevsInfo(
                idx,
                * self.medDict.generateHeaderCandidates(self.hDataset.getHeaderInfo(idx))
            )
            print("=" * 30)
        self.hDataset.setDatasetAbbrevs(self.medDict.datasetAbbrevDetected)                                 ;print(self.hDataset.datasetAbbrevs) ;[print(f"'{self.hDataset.tokenizedHeaders[idx]}',") for idx in self.hRange]

        for idx in self.hRange:
            if not self.hDataset.doesntContainAbbrevs(idx):
                self.hContext[idx] = self.hDataset.generateHeaderContext(idx)


    def _generateCachedEmbeddings(self):
        self._cacheCollectionEmbeddings(self.hDataset.datasetAbbrevs)
        self._cacheCollectionEmbeddings(self.hDataset.tokenizedHeaders)                                                 # ; [print(k, v[:3]) for k,v in self.cachedEmbeddings.items()]


    def _cacheCollectionEmbeddings(self, collection: Union[Set[str], List[str]]):
        collection = {el for el in collection if el not in self.cachedEmbeddings}
        batch, batchPos = Bert.createBatches(collection)
        embeddings = self.bert(batch)                                                                                   ;print(f"Cached {len(collection)} new embeddings")
        for el in collection:
            pos = batchPos[el]
            self.cachedEmbeddings[el] = embeddings[pos[0]][pos[1]]



    def _calcGlobalAbbrevScores(self):

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
        self.headersCands = {}
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
                        cand = HeaderCand((wholeFF,), wholeFF, wholeScore, True)
                        pos  = wholeBatchPos[wholeFF]
                        emb  = wholeEmbeddings[pos[0]][pos[1]]
                        ctxScores = Bert.cos(emb, ctxEmbeddings)
                        cand.setContextScores(ctxScores)
                        headerCands.append(cand)                                                                        # ;print(f"\t{wholeFF} : {wholeFF} = {wholeScore}")

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
                        cand = HeaderCand(headerAbbrevsFFs, headerFF, partialScore, False)                              # ;print(f"\t{headerAbbrevsFFs} : {headerFF} = {partialScore}")
                        emb  = partialEmbeddings[pos[0]][pos[1]]
                        ctxScores = Bert.cos(emb, ctxEmbeddings)
                        cand.setContextScores(ctxScores)
                        headerCands.append(cand)

                headerCands = self._firstRoundFiltering(idx, headerCands)                                               # self._printCandidates(idx, headerCands, ctx)
                self.headersCands[idx] = headerCands






    def _firstRoundFiltering(self, idx:int, headerCands:List[HeaderCand], FIRST_ROUND_THRS: float = 0.82):
        headerCands = sorted(headerCands, key=lambda x: x.score, reverse=True)
        pheaderAbbrevs: Tuple = self.hDataset.partialAbbrevsDetected[idx]
        toRmv = set()
        for i, cand in enumerate(headerCands):
            if cand.score <= FIRST_ROUND_THRS:

                if cand.meanCtxScore <= FIRST_ROUND_THRS or (
                   cand.isWholeHeader and self.globalAbbrevScores[self.hDataset.headers[idx]][cand.headerFF] <= FIRST_ROUND_THRS):
                    toRmv.add(i)                                                                                        # ;print(f"\t\tRemove : {cand.headerFF}")
                else:
                    for hA, hAI in zip(pheaderAbbrevs, cand.headerAbbrevsFFs):
                        if self.globalAbbrevScores[hA][hAI] <= FIRST_ROUND_THRS:
                            toRmv.add(i)                                                                                # ;print(f"\t\tRemove : {cand.headerFF}")
                            break

        return [headerCands[i] for i in range(len(headerCands)) if i not in toRmv]




    def _findSeeds(self):
        def key(cand):
            return self.hDataset.headers[idx] if cand.isWholeHeader else pAbbrevs[0]

        self.seeds = {}
        for idx, cands in tqdm(self.headersCands.items()):
            if not cands:
                continue

            # header = self.hDataset.headers[idx]
            pAbbrevs: Tuple = self.hDataset.partialAbbrevsDetected[idx]

            # complex headers are not considered seeds
            if not(len(pAbbrevs) == 1 or self.hDataset.isWholeHeaderAbbrev(idx)):
                continue

            # single high score candidate
            if len(cands) == 1:
                if cands[0].score > 0.85 and self._getMeanGlobal(idx, cands[0], pAbbrevs) > 0.85:
                    self.seeds[key(cands[0])] = cands[0].headerAbbrevsFFs[0]
                continue

            # single cand with scores greater than 90%
            isSeed = True
            top1Score, top1GlobalScore = cands[0].score, self._getMeanGlobal(idx,cands[0],pAbbrevs)
            if top1Score > 0.9 and top1GlobalScore > 0.9:
                for cand2 in cands[1:]:
                    if cand2.score > 0.9 and self._getMeanGlobal(idx,cand2,pAbbrevs) > 0.9:
                        isSeed = False
                        break
                if isSeed:
                    self.seeds[key(cands[0])] = cands[0].headerAbbrevsFFs[0]
                    continue


            highScoreCands = []
            k = ''
            for cand in cands:
                if cand.score >= 0.93 and self._getMeanGlobal(idx,cand,pAbbrevs) >= 0.93:
                    highScoreCands.append(cand.headerAbbrevsFFs[0])
                    k = self.hDataset.headers[idx] if cand.isWholeHeader else pAbbrevs[0]
            if len(highScoreCands) > 0:
                nearDuplicates = groupNearDuplicates(highScoreCands, strict= False)
                if len(nearDuplicates) == 1:
                    self.seeds[k] = next(iter(nearDuplicates))

        [print(k,v) for k,v in self.seeds.items()]



    def _setSeedScores(self):
        seedsBatches, seedsBatchPos = Bert.createBatches(self.seeds)
        seedsEmbeddings = self.bert(seedsBatches)

        for idx, headerCands in tqdm(self.headersCands.items()):
            # print(f"\n>> Calc Header [{idx}] : {self.hDataset.tokenizedHeaders[idx]}")
            hAbbrevs = self.hDataset.getHeaderAbbrevs(idx)
            overlap = set()
            for cA in self.seeds.keys() & hAbbrevs:
                pos = seedsBatchPos[cA]
                overlap.add(pos)

            for cand in headerCands:
                candEmbedding = self.bert([[cand.headerFF]])[0]
                seedScores = Bert.cos(candEmbedding, seedsEmbeddings)
                seedScores = [batch[jpos].item() for ib, batch in enumerate(seedScores) for jpos in range(batch.shape[0]) if (ib, jpos) not in overlap]
                cand.setSeedScores(seedScores)

            # self._printCandidates(idx, headerCands, [self.hDataset.tokenizedHeaders[ctxIdx] for ctxIdx in self.hContext[idx]], self.seeds)
        # self._printTable()


    def _weightAvgScores(self):
        candScoreW = 0.4
        globalW = 0.2
        ctxW = 0.3
        ssW = 0.3

        for idx, headerCands in self.headersCands.items():
            pAbbrev = self.hDataset.partialAbbrevsDetected[idx]
            for cand in headerCands:
                meanGlobal = self._getMeanGlobal(idx,cand,pAbbrev)
                wAvg = candScoreW * cand.score + globalW * meanGlobal + ctxW * cand.meanCtxScore + ssW * cand.meanSeedScores
                cand.weightAvgScore = wAvg
            self.headersCands[idx] = sorted(headerCands, key=lambda x: x.weightAvgScore, reverse=True)




    def _getGlobalScore(self, abbrev, ff):
        return self.globalAbbrevScores[abbrev][ff]

    def _getMeanGlobal(self, idx, cand:HeaderCand, pheaderAbbrevs):
        globalScores = []
        if cand.isWholeHeader:
            globalScores.append(self.globalAbbrevScores[self.hDataset.headers[idx]][cand.headerFF])
        elif pheaderAbbrevs:
            for hA, hAI in zip(pheaderAbbrevs, cand.headerAbbrevsFFs):
                globalScores.append(self.globalAbbrevScores[hA][hAI])

        return mean(globalScores)



# ======================================================================================================


    def _printCandidates(self, idx, headerCands, ctx, seeds= None):
        pheaderAbbrevs:Tuple = self.hDataset.partialAbbrevsDetected[idx]

        headerCands = sorted(headerCands, key=lambda x: x.score, reverse=True)

        for cand in headerCands:
            print(f"\tCand = '{cand.headerAbbrevsFFs}'")
            globalS = ""
            if cand.isWholeHeader:
                globalS += f"\t\tGlobal({self.hDataset.headers[idx]}, {cand.headerFF}) = {self.globalAbbrevScores[self.hDataset.headers[idx]][cand.headerFF]}\n"
            if pheaderAbbrevs:
                for hA, hAI in zip(pheaderAbbrevs, cand.headerAbbrevsFFs):
                    try:
                        globalS += f"\t\tGlobal({hA}, {hAI}) = {self.globalAbbrevScores[hA][hAI]}\n"
                    except KeyError:
                        pass
            print(globalS, end='')
            cand.printCand(ctx, seeds)



    def _printTable(self):
        for idx, headerCands in self.headersCands.items():
            header = self.hDataset.tokenizedHeaders[idx]
            pheaderAbbrevs: Tuple = self.hDataset.partialAbbrevsDetected[idx]
            print(f">> Header [{idx}] : {header}")
            for cand in headerCands:
                if cand.weightAvgScore is not None:
                    candInfo = "\tWA = " + str(round(cand.weightAvgScore, 5)) + '\t'
                else:
                    candInfo = ''
                candInfo += "\tS = " + str(round(cand.score*100, 3))
                candInfo += "\t\tCS = " + str(round(cand.meanCtxScore * 100, 3))

                if cand.seedScores is not None:
                    candInfo += "\t\tSS = " + str(round(cand.meanSeedScores * 100, 3))

                candInfo += "\t\tGS = ("

                if cand.isWholeHeader:
                    candInfo += str(round(self.globalAbbrevScores[self.hDataset.headers[idx]][cand.headerFF]*100, 3))
                if pheaderAbbrevs:
                    for hA, hAI in zip(pheaderAbbrevs, cand.headerAbbrevsFFs):
                        try:
                            candInfo += str(round(self.globalAbbrevScores[hA][hAI]*100, 3)) + ", "
                        except KeyError:
                            pass
                candInfo += f' )\t\t {cand.headerFF}'
                print(candInfo)
            print()






