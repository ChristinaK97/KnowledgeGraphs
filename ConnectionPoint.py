import pandas as pd
from torch.utils.data import DataLoader

from HeaderTokenizer import HeaderTokenizer
from MedicalDictionary import MedicalDictionary
from util.util import SentencesDataset

path = "C:\\Users\\karal\\OneDrive\\Υπολογιστής\\clinical-abbreviations-1.0.2\\clinical-abbreviations-1.0.2" \
       "\\metainventory\\Metainventory_Version1.0.0.csv"

inputDataset = "resources\\Data_test_Encrypt_Repaired.csv"

headers = pd.read_csv(inputDataset, delimiter=";").columns.to_list()
hTokenizer = HeaderTokenizer(headers)



medDict = MedicalDictionary(dictionaryCSVPath=path, datasetAlphabet = hTokenizer.headersAlphabet)

for idx, (header, headerInputs) in enumerate(zip(headers, hTokenizer.getHeaderInputs())):
    hTokenizer.printHeaderInfo(idx)
    medDict.generateCandidates(header, headerInputs)
    print("="*30)


"""
md = MedicalDictionary(dictionaryCSVPath=path)
for h in headers:
       md.generateAllPossibleCandidates(h)
       print('\n=================================\n')
"""



