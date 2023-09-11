import pandas as pd

data = {'Column1': [1, 2, 3, 4, 5],
        'Column2': ['A', 'B', 'C', 'D', 'E'],
        'Column3': [0.1, 0.2, 0.3, 0.4, 0.5]}

df = pd.DataFrame(data)
print(df)
print(
    df.iloc[1:]
)
print(
    len(df)
)