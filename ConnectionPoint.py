import pandas as pd
from NinjaUtil import ninjaSpit

inputDataset = "Data_test_Encrypt_Repaired.csv"
headers = pd.read_csv(inputDataset, delimiter=";").columns.to_list()

ninjaHeaders = ninjaSpit(headers)



