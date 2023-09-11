import random
from typing import List, Union, Set, Dict, Tuple

import numpy as np
import torch
from torch.nn import Module
import torch.nn.functional as F
from torch import Tensor
from torch.cuda import is_available, get_device_name, current_device
from transformers import BertTokenizer, BertModel


class BertSimilarityModel(Module):

    model_name = 'monologg/biobert_v1.1_pubmed'
    BATCH_SIZE = 16

    def __init__(self):
        Module.__init__(self)

        BertSimilarityModel.set_seed()
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")                                      ;print(self.device, is_available(), get_device_name(current_device()))
        self.tokenizer = BertTokenizer.from_pretrained(BertSimilarityModel.model_name)
        self.model = BertModel.from_pretrained(BertSimilarityModel.model_name,
                                               output_hidden_states=True)
        self.model.to(self.device)
        self.model.eval()


    def forward(self, batches: List[List[str]]):
        tokenizedBatches = self._tokenizeBatches(batches)                                                               # ;print(tokenizedBatches)
        batches_embeddings = []

        for batch in tokenizedBatches:
            with torch.no_grad():
                # outputs.shape: [# sentences in the batch, # tokens in the longest sentence, 768]
                outputs = self.model(**batch).last_hidden_state                                                         # ;print('\n', batch)
            batch_embeddings = outputs[:, 0, :]                                                                         # ;print(outputs.shape) ;print(batch_embeddings.shape)
            batches_embeddings.append(batch_embeddings)
        return batches_embeddings


    def _tokenizeBatches(self, batches: List[List[str]]):
        return [
            self.tokenizer(batch, padding=True, truncation=True, return_tensors='pt')
            .to(self.device)
        for batch in batches]


    @staticmethod
    def createBatches(sentences: Union[Set[str], List[str], Dict[Tuple[str],str]]):
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

            if batchPos == BertSimilarityModel.BATCH_SIZE or sent_i == len(sentences)-1:
                batches.append(currBatch)
                currBatch = []
                batchIdx += 1
                batchPos = 0

        return batches, sentBatchPos



    @staticmethod
    def cos(tgtEmbedding: Tensor, batchesEmbeddings: List[Tensor]) -> List[Tensor]:
        """
        :param tgtEmbedding: torch.Size([1,768])
        :param batchesEmbeddings: List[Tensor[# sent in the batch, 768]] or List[Tensor[1, 768]] or List[Tensor[768]]
                len(batchesEmbeddings) == # batches
        """
        dim = max(len(tgtEmbedding.shape), len(batchesEmbeddings[0].shape)) - 1
        similarityScore = [
            F.cosine_similarity(tgtEmbedding, batchEmbeddings, dim=dim)
            for batchEmbeddings in batchesEmbeddings]
        assert len(similarityScore) == len(batchesEmbeddings)
        return similarityScore



    @staticmethod
    def set_seed(seed_val: int = 0):
        """Set random seed for reproducible results."""
        random.seed(seed_val)
        np.random.seed(seed_val)
        torch.manual_seed(seed_val)
        torch.cuda.manual_seed(seed_val)
        torch.cuda.manual_seed_all(seed_val)
        torch.backends.cudnn.benchmark = False
        torch.backends.cudnn.deterministic = True




"""
batches_ = [['CAD'], ['Coronary Artery Disease', 'Computer Assisted Diagnosis'], ['Coronary Artery Disease', 'Computer Assisted Diagnosis']]
bert = BertSimilarityModel()
embeddings = bert(batches_)
for batch_, batchEmbs in zip(batches_, embeddings):
    for sent, sentEmb in zip(batch_, batchEmbs):
        print(f"{sent}\t:\t{sentEmb.shape}  {sentEmb[:3]}")

singleEmb:Tensor = embeddings[0]

twoEmbs = embeddings[1]
print(twoEmbs.shape)

sim = BertSimilarityModel.cos(singleEmb, twoEmbs)
print(sim, '\n==============')

twoEmbs = [emb for emb in embeddings[1]]
sim = BertSimilarityModel.cos(singleEmb, twoEmbs)
print(sim)

"""