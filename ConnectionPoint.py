import pandas as pd
from spacy.tokens import Doc, Span
from HeaderTokenizer import HeaderTokenizer
from MedicalDictionary import MedicalDictionary

path = "C:\\Users\\karal\\OneDrive\\Υπολογιστής\\clinical-abbreviations-1.0.2\\clinical-abbreviations-1.0.2" \
       "\\metainventory\\Metainventory_Version1.0.0.csv"
inputDataset = "Data_test_Encrypt_Repaired.csv"

headers = pd.read_csv(inputDataset, delimiter=";").columns.to_list()
HeaderTokenizer(headers)


