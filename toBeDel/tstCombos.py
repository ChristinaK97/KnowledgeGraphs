

from itertools import product

# Sample dictionaries with key sets
dict1 = {'A': 1, 'B': 2}
dict2 = {'X': 3, 'Y': 4}
dict3 = {'P': 5, 'Q': 6}

dict_ = {'d1' : dict1, 'd2': dict2, 'd3': dict3}
spans = {'d1' : (2,3), 'd2': (0,1), 'd3': (4,7)}

dims = {}
dictionaries = []

# Sort the dictionary keys based on the first element of each span
sorted_keys = sorted(spans.keys(), key=lambda k: spans[k][0])

# Create a list of dictionaries in the sorted order
dictionariesList = [dict_[key] for key in sorted_keys]

# Output the sorted list of dictionaries
for dictionary in dictionariesList:
    print(dictionary)

exit(0)
for dim, (headerAbbrev, headerAbbrevDict) in enumerate(dict_.items()):
    dims[headerAbbrev] = dim
    dictionaries.append(headerAbbrevDict)

print(dims)
key_sets = [d.keys() for d in dictionaries]
# Calculate the Cartesian product of the key sets
cartesian_product = product(*key_sets)

# Convert the Cartesian product into a list of tuples
result = list(cartesian_product)

# Output the result
print(result)



# Create a list of dictionaries
dict_list = [dict1, dict2, dict3]

# Extract key sets from the dictionaries and store them with their corresponding dictionaries
key_sets = [(set(d.keys()), d) for d in dict_list]

# Calculate the Cartesian product of the key sets
cartesian_product = product(*[ks[0] for ks in key_sets])

# Convert the Cartesian product into a list of tuples, along with their corresponding dictionaries
result = [(ks[1], combination) for ks, combination in zip(key_sets, cartesian_product)]

# Output the result
for dictionary, combination in result:
    print(f"Dictionary: {dictionary}, Combination: {combination}")

