import torch
isCudaAvailableMessage = \
    "Cuda available : " + torch.cuda.get_device_name(torch.cuda.current_device()) if torch.cuda.is_available() else \
    "Cuda NOT available"
print(isCudaAvailableMessage)

import pandas as pd
from pathlib import Path

from source.InterpretHeaders import InterpretHeaders

import os

def list_files_in_directory():
    directory = Path("/KnowledgeGraphsApp")
    for root, dirs, files in os.walk(directory):
        for file in files:
            file_path = os.path.join(root, file)
            print(file_path)

list_files_in_directory()
path = Path("/KnowledgeGraphsApp/resources/Metainventory_Version1.0.0.csv")
inputDataset = Path("resources/Data_test_Encrypt_Repaired.csv")
outputPath = Path("resources/abbrevExpansionResults.json")

headers = pd.read_csv(inputDataset, delimiter=";").columns.to_list()
print("Headers : ", headers[0:5])

InterpretHeaders(headers, path, outputPath)
# SciSpacyEntityLinker(outputPath)

"""
md = MedicalDictionary(dictionaryCSVPath=path)
for h in headers:
       md.generateAllPossibleCandidates(h)
       print('\n=================================\n')
"""



