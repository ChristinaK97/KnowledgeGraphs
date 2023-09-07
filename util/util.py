from torch.utils.data import Dataset, DataLoader


class SentencesDataset(Dataset):
    def __init__(self, data):
        self.data = data

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        return self.data[idx]


def createDataloader(headers, sentences, batchSize=16):
    dataloader = DataLoader(SentencesDataset(sentences),
                            batch_size=batchSize,
                            collate_fn=lambda x: x,
                            shuffle=False, drop_last=False)

    for batch in dataloader:
        print(batch)
    headerBatchIdxPos = []
    batchIdx = -1
    for idx, header in enumerate(headers):
        if idx % batchSize == 0:
            batchIdx += 1
            batchPos = 0
        headerBatchIdxPos.append((batchIdx, batchPos))                                                                  ;print(header, batchIdx, batchPos)
        batchPos += 1

    return dataloader, headerBatchIdxPos


def generateHeadersContext(headers, winSize=1):
    sentences = []
    for idx, header in enumerate(headers):

        mostFrontIdx = max(0, idx - winSize)
        mostRearIdx = min(len(headers), idx + winSize + 1)
        nContext = mostRearIdx - mostFrontIdx - 1
        remainingContext = 2*winSize - nContext                                                                         ;print(f"{idx} {header} fr={mostFrontIdx}  rr={mostRearIdx}  nC={nContext} rm={remainingContext}")

        if remainingContext > 0:
            if mostFrontIdx == 0:
                while remainingContext > 0 and mostRearIdx < len(headers):
                    mostRearIdx += 1
                    remainingContext -= 1
            elif mostRearIdx == len(headers):
                while remainingContext > 0 and mostFrontIdx > 0:
                    mostFrontIdx -= 1
                    remainingContext -= 1

        sentences.append(headers[mostFrontIdx:mostRearIdx])                                                             ;print(sentences[-1])
    return sentences



# sentences = generateHeadersContext(headers)
# dataloader, headerBatchIdxPos = createDataloader(headers, sentences)

