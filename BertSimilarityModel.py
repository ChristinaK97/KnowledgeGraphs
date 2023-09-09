import random

import numpy as np
import torch
from torch.nn import Module
from torch.cuda import is_available, get_device_name, current_device
from transformers import BertTokenizer, BertModel


class BertSimilarityModel(Module):

    model_name = 'monologg/biobert_v1.1_pubmed'

    def __init__(self):
        Module.__init__(self)

        BertSimilarityModel.set_seed()
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")                                      ;print(self.device, is_available(), get_device_name(current_device()))
        self.tokenizer = BertTokenizer.from_pretrained(BertSimilarityModel.model_name)
        self.model = BertModel.from_pretrained(BertSimilarityModel.model_name,
                                               output_hidden_states=True)
        self.model.to(self.device)
        self.model.eval()


    def forward(self, batches):
        tokenizedBatches = self._tokenizeBatches(batches)                                                               # ;print(tokenizedBatches)
        batches_embeddings = []

        for batch in tokenizedBatches:
            with torch.no_grad():
                # outputs.shape: [# sentences in the batch, # tokens in the longest sentence, 768]
                outputs = self.model(**batch).last_hidden_state                                                         # ;print('\n', batch)
            batch_embeddings = outputs[:, 0, :]                                                                         # ;print(outputs.shape) ;print(batch_embeddings.shape)
            batches_embeddings.append(batch_embeddings)
        return batches_embeddings


    def _tokenizeBatches(self, batches):
        return [
            self.tokenizer(batch, padding=True, truncation=True, return_tensors='pt')
            .to(self.device)
        for batch in batches]


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



batches = [['Hello', 'Hello there'], ['This', 'sentence'], ['batch2_sent0']]
bert = BertSimilarityModel()
embeddings = bert(batches)
for batch, batchEmbs in zip(batches, embeddings):
    for sent, sentEmb in zip(batch, batchEmbs):
        print(f"{sent}\t:\t{sentEmb[:3]}")





