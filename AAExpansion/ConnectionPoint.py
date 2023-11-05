import torch
if torch.cuda.is_available():
    print("Cuda available :",  torch.cuda.get_device_name(torch.cuda.current_device()))
else:
    print("Cuda NOT available")

import pandas as pd

from source.InterpretHeaders import InterpretHeaders

path = "resources\\Metainventory_Version1.0.0.csv"
inputDataset = "resources\\Data_test_Encrypt_Repaired.csv"
outputPath = "resources\\abbrevExpansionResults.json"

# headers = pd.read_csv(inputDataset, delimiter=";").columns.to_list()

# InterpretHeaders(headers, path, outputPath)
# SciSpacyEntityLinker(outputPath)

"""
md = MedicalDictionary(dictionaryCSVPath=path)
for h in headers:
       md.generateAllPossibleCandidates(h)
       print('\n=================================\n')
"""



