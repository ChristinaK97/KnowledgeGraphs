from typing import List

from HeadersDataset import HeadersDataset
from MedicalDictionary import MedicalDictionary


def interpretHeaders(headers: List[str], medDictPath: str):
    hDataset = HeadersDataset(headers)
    medDict = MedicalDictionary(dictionaryCSVPath=medDictPath, datasetAlphabet=hDataset.headersAlphabet)

    for idx in range(len(headers)):
        hDataset.printHeaderInfo(idx)
        hDataset.setHeaderAbbrevsInfo(
            idx,
            *medDict.generateHeaderCandidates(hDataset.getHeaderInfo(idx))
        )
        print("=" * 30)
    hDataset.setDatasetAbbrevs(medDict.datasetAbbrevDetected)
    print(hDataset.datasetAbbrevs)
    [print(f"'{hDataset.tokenizedHeaders[idx]}',") for idx in range(hDataset.nHeaders)]

    hContext = {}
    for idx in range(hDataset.nHeaders):
        if not hDataset.doesntContainAbbrevs(idx):
            hContext[idx] = hDataset.generateHeaderContext(idx)


# def generateContextForHeadersWithAbbrevs():

