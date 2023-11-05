import torch

from AAExpansion.source.InterpretHeaders import InterpretHeaders

print("Sees cuda ? ", torch.cuda.is_available())

import time
time.sleep(120)


#import pandas as pd


path = "resources\\Metainventory_Version1.0.0.csv"
inputDataset = "resources\\Data_test_Encrypt_Repaired.csv"
outputPath = "resources\\abbrevExpansionResults.json"

#headers = pd.read_csv(inputDataset, delimiter=";").columns.to_list()

# InterpretHeaders(headers, path, outputPath)
# SciSpacyEntityLinker(outputPath)

"""
md = MedicalDictionary(dictionaryCSVPath=path)
for h in headers:
       md.generateAllPossibleCandidates(h)
       print('\n=================================\n')
"""



