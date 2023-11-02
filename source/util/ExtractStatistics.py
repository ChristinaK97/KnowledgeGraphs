import pandas as pd
from matplotlib import pyplot as plt
from pytrie import SortedStringTrie

from MedicalDictionary import MedicalDictionary

pd.set_option('display.max_rows', None)
pd.set_option('display.max_columns', None)
pd.set_option('display.width', 1000)
path = "..\\resources\\Metainventory_Version1.0.0.csv"
AA:str = "SF"
FF:str = "LF"

metaInv = pd.read_csv(path, delimiter='|', encoding='utf-8', low_memory=False)
print(metaInv.head())

print(f"# unique AAs = {len(metaInv[AA].unique())}")
tries = MedicalDictionary(dictionaryCSVPath=path) #, datasetAlphabet={'a'})

FFcount = {}
for letter, lTrie in tries.letterTries.items():
    lTrie:SortedStringTrie
    for aa in lTrie.keys():
        nCands = len(lTrie.get(aa).keys())
        FFcount[nCands] = FFcount[nCands]+1 if nCands in FFcount else 1

print(FFcount)
data = FFcount

# Sort the data dictionary by keys
sorted_data = dict(sorted(data.items()))

# Extract counts as a list
counts = list(sorted_data.values())

# Create a horizontal histogram
plt.barh(range(len(sorted_data)), counts)

# Set y-axis labels as keys from the sorted dictionary
plt.yticks(range(len(sorted_data)), sorted_data.keys())

# Set the title and labels
plt.title('Horizontal Histogram of Counts (Sorted)')
plt.xlabel('Counts')

# Show the plot
plt.show()







