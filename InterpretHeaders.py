from typing import List, Union, Tuple, Dict, Set

import torch
from torch import Tensor

from HeadersDataset import HeadersDataset
from MedicalDictionary import MedicalDictionary


class HeaderCandScore:
    def __init__(self, headerFF: str, score: torch.double, isWholeHeader:bool):

        self.headerFF = headerFF
        self.score = score
        self.isWholeHeader = isWholeHeader
        self.contextScores = []

    def addContextScore(self, ctxScore: torch.double):
        self.contextScores.append(ctxScore)

    def setContextScores(self, ctxScores):
        self.contextScores = ctxScores

    def printCand(self, ctxHeaders):
        print(f"\t\tHFF =  '{self.headerFF}'\tIsWH = {self.isWholeHeader}\t Score =  {self.score}\n\t\tCtx Scores = ")
        for hctx, ctxScore in zip(ctxHeaders, self.contextScores):
            print(f"\t\t\t\tctx = {hctx}\t score = {ctxScore}")


class InterpretHeaders:

    def __init__(self, headers: List[str], medDictPath: str):

        self.hDataset = HeadersDataset(headers)
        self.medDict  = MedicalDictionary(dictionaryCSVPath=medDictPath,
                                          datasetAlphabet=self.hDataset.headersAlphabet)
        self.hRange = range(self.hDataset.__sizeof__())
        self.hContext = {}
        self._setup()

        print(">> Inter")
        self.cachedEmbeddings: Dict[str, Tensor] = {}
        self._generateCachedEmbeddings()

        self.globalAbbrevScores: Dict[str, Dict[str,torch.double]] = {}
        self._calcGlobalAbbrevScores()
        # print(self.hDataset.datasetAbbrevs)
        # [[print(f'{abbrev} ({self.cachedEmbeddings[abbrev]}), {ff} = {score}') for ff, score in abbrevScores.items()] for abbrev, abbrevScores in self.globalAbbrevScores.items()]

        self._calcHeaderScores()

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
        self._cacheCollectionEmbeddings(self.hDataset.tokenizedHeaders)
        # [print(k, v) for k,v in self.cachedEmbeddings.items()]


    def _cacheCollectionEmbeddings(self, collection: Union[Set[str], List[str]]):
        collection = {el for el in collection if el not in self.cachedEmbeddings}
        batch, batchPos = InterpretHeaders.createBatches(collection)
        embeddings = self._embedBatches(batch)                                                                          ;print(f"Cached {len(collection)} new embeddings")
        for el in collection:
            pos = batchPos[el]
            self.cachedEmbeddings[el] = embeddings[pos[0]][pos[1]]



    def _calcGlobalAbbrevScores(self):

        for abbrev in self.hDataset.datasetAbbrevs:
            self.globalAbbrevScores[abbrev] = {}
            abbrevEmbedding = self.cachedEmbeddings[abbrev]

            fullForms = self.medDict.getExactMatch(abbrev)
            ffBatches, ffBatchPos = InterpretHeaders.createBatches(fullForms)
            ffEmbeddings = self._embedBatches(ffBatches)

            globalAbbrevScores = self._calcScores(abbrevEmbedding, ffEmbeddings)
            for fullForm in fullForms:
                pos = ffBatchPos[fullForm]
                self.globalAbbrevScores[abbrev][fullForm] = globalAbbrevScores[pos[0]][pos[1]]


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
        for idx in self.hRange:
            if not self.hDataset.doesntContainAbbrevs(idx):
                headerCandsScores = {}
                tHeader = self.hDataset.tokenizedHeaders[idx]                                                           ;print(f"\nCalc for [{idx}] : {tHeader}")

                ctx = [self.hDataset.tokenizedHeaders[ctxIdx] for ctxIdx in self.hContext[idx]]
                ctxEmbeddings = [[self.cachedEmbeddings[ctxHeader]] for ctxHeader in ctx]

                # ======================================================================================================
                # The whole header is an abbreviation
                # ======================================================================================================
                if self.hDataset.isWholeHeaderAbbrev(idx):
                    wholeAbbrev = self.hDataset.headers[idx]                                                            # ;print(f"\tAbbrevs = Whole Header {wholeAbbrev}")

                    wholeFFs = self.medDict.getExactMatch(wholeAbbrev)
                    wholeBatches, wholeBatchPos = InterpretHeaders.createBatches(wholeFFs)
                    wholeEmbeddings = self._embedBatches(wholeBatches)

                    for wholeFF, wholeScore in self.globalAbbrevScores[wholeAbbrev].items():
                        cand = HeaderCandScore(wholeFF, wholeScore, True)
                        pos  = wholeBatchPos[wholeFF]
                        emb  = wholeEmbeddings[pos[0]][pos[1]]
                        ctxScores = self._calcScores(emb, ctxEmbeddings)
                        cand.setContextScores(ctxScores)
                        headerCandsScores[(wholeFF,)] = cand                                                            # ;print(f"\t{wholeFF} : {wholeFF} = {wholeScore}")

                # ======================================================================================================
                # The header contains partial abbreviations
                # ======================================================================================================
                if self.hDataset.containsPartialAbbrevs(idx):

                    partialAbbrevs = self.hDataset.partialAbbrevsDetected[idx]
                    partialCands = self.hDataset.partialCands[idx]

                    headerEmbedding = self.cachedEmbeddings[tHeader]                                                    # ;print(f"\tPartial Abbrevs = {partialAbbrevs}\tEmb = {headerEmbedding}") # ;[print(headerAbbrevsFFs, headerFF) for headerAbbrevsFFs, headerFF in partialCands.items()]

                    # List[List[str]], Dict[Tuple[str], Tuple[int,int]]
                    partialBatches, partialBatchPos = InterpretHeaders.createBatches(partialCands)
                    partialEmbeddings = self._embedBatches(partialBatches)
                    partialScores = self._calcScores(headerEmbedding, partialEmbeddings)

                    for headerAbbrevsFFs, headerFF in partialCands.items():
                        pos = partialBatchPos[headerAbbrevsFFs]
                        partialScore = partialScores[pos[0]][pos[1]]
                        cand = HeaderCandScore(headerFF, partialScore, False)                                           # ;print(f"\t{headerAbbrevsFFs} : {headerFF} = {partialScore}")
                        emb  = partialEmbeddings[pos[0]][pos[1]]
                        ctxScores = self._calcScores(emb, ctxEmbeddings)
                        cand.setContextScores(ctxScores)
                        headerCandsScores[headerAbbrevsFFs] = cand

                for key, value in headerCandsScores.items():
                    print(f"\tCand = '{key}'")
                    value.printCand(ctx)






    def _calcScores(self, tgtEmbedding, ffEmbeddingsBatches):
        return [[tgtEmbedding + " * " + ffEmbedding for ffEmbedding in ffEmbeddingsBatch] for ffEmbeddingsBatch in ffEmbeddingsBatches]
        # return [tgtEmbedding * ffEmbeddingsBatch for ffEmbeddingsBatch in ffEmbeddingsBatches]


    @staticmethod
    def createBatches(sentences, batchSize: int = 16):
        batches = []
        currBatch = []
        batchIdx = 0
        batchPos = 0
        sentBatchPos = {}
        isDict = isinstance(sentences, dict)

        for sent_i, sentence in enumerate(sentences):

            currBatch.append(sentences[sentence] if isDict else sentence)
            sentBatchPos[sentence] = (batchIdx, batchPos)
            batchPos += 1

            if batchPos == batchSize or sent_i == len(sentences)-1:
                batches.append(currBatch)
                currBatch = []
                batchIdx += 1
                batchPos = 0

        return batches, sentBatchPos


    def _embedBatches(self, batches):
        embeddings = []
        counter = 0
        for batch in batches:
            batchEmbeddings = []
            for sentence in batch:
                batchEmbeddings.append(sentence)
                counter += 1
            # batchEmbeddings = torch.tensor(batchEmbeddings, requires_grad=False, dtype=torch.double)
            embeddings.append(batchEmbeddings)
        return embeddings






"""
sentences = [f'Sent {i}' for i in range(23)]
batches, sentBatchIdxPos = InterpretHeaders.createBatches(sentences, batchSize=4)
[print(batch) for batch in batches]
[print(sentences[i], sentBatchIdxPos[i]) for i in range(len(sentences))]

"""








