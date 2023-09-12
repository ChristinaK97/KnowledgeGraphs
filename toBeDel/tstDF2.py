from functools import reduce

import pandas as pd

from util.UnionFind import UnionFind

# Sample DataFrame
data = {
    'abbrev': ['A', 'B', 'A', 'C', 'B', 'A'],
    'headerAbbrevsFFs': [{'X', 'Y'}, {'X', 'Z'}, {'X'}, {'Y', 'Z'}, {'Z'}, {'K'}],
    'other_column': [1, 2, 3, 4, 5, 6]
}

df = pd.DataFrame(data)
print(df)
"""
for abbrev in df['abbrev'].unique():
    print(">> ", abbrev)
    abbrevDF = df[df['abbrev'] == abbrev].reset_index(drop=True)
    uf = UnionFind(len(abbrevDF))
    for i, candi in abbrevDF.iterrows():
        for j, candj in abbrevDF[i+1:].iterrows():
            if candi.headerAbbrevsFFs & candj.headerAbbrevsFFs:
                uf.union(i,j)
    uf = uf.getSets()
    print(uf)
    for groupId, group in enumerate(uf):
        for row in group:
            abbrevDF.at[row, 'group'] = groupId

    print(abbrevDF)
"""



# Your DataFrame
data = {
    'abbrev': ['tm', 'tm'],
    'headerAbbrevsFFs': [{'muscle'}, {'capacity'}],
    'index': [0, 0],
    'score': [0.940422, 0.962015],
    'meanCtxScore': [0.885954, 0.871753],
    'meanSeedScore': [0.875165, 0.841122],
    'globalScores': [0.909045, 0.862409],
    'group': [2, 1]
}

df = pd.DataFrame(data)

df = pd.DataFrame(data)

# Use reduce and set union to combine sets
all_sets = reduce(lambda x, y: x | y, df['headerAbbrevsFFs'])

print(all_sets)


