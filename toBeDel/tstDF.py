import numpy as np
import pandas as pd

pd.set_option('display.max_rows', None)
pd.set_option('display.max_columns', None)
pd.set_option('display.width', 400)

# Sample data
data = {
    'score': [0.915649, 0.912721, 0.911595, 0.903950, 0.902310, 0.899605, 0.898806, 0.897376, 0.895982, 0.891343,
              0.885522, 0.885043],
    'meanCtxScore': [0.856822, 0.854642, 0.874920, 0.844869, 0.838369, 0.834970, 0.835930, 0.837088, 0.836032, 0.843144,
                     0.845095, 0.841313],
    'meanSeedScore': [0.871212, 0.875027, 0.880947, 0.869476, 0.866245, 0.862756, 0.864908, 0.864927, 0.859120,
                      0.849449, 0.860393, 0.877028],
    'globalScores': [(0.9379259347915649,), (0.9293253421783447,), (0.9130058288574219,), (0.9008923172950745,),
                     (0.9085059762001038,), (0.912401556968689,), (0.9256924986839294,), (0.9096527695655823,),
                     (0.8959819078445435,), (0.8913432359695435,), (0.906722903251648,), (0.9178766012191772,)],
    'group': [(2,), (2,), (7,), (2,), (4,), (4,), (4,), (2,), (0,), (0,), (9,), (3,)],
    'headerFF': [
        'left anterior descending 1', 'left anterior descending branch 1', 'lactic acid dehydrogenase 1',
        'left anterior descending artery 1', 'left anterior descending coronary artery 1',
        'left descending coronary artery 1', 'left anterior descending coronary 1',
        'left anterior descending arteries 1', 'leucocyte adhesion deficiency 1',
        'leucocyte adhesion deficiency type 1', 'left axis deviation 1', 'large artery disease 1'
    ],
    'isWholeHeader': [False, True, False, False, True, False, False, False, False, False, False, False]
}
data['headerAbbrevsFF'] = [(el,) for el in data['headerFF']]

df = pd.DataFrame(data)


def custom_agg(series):
    # Check the data type of the series
    if np.issubdtype(series.dtype, np.number):
        return series.max()
    elif series.dtype == 'object' and all(isinstance(x, tuple) for x in series):
        # Get the maximum value per tuple position
        if isinstance(series.iloc[0][0], str):
            return list(series)
        else:
            max_values = [max(item) for item in zip(*series)]
            return max_values
    elif series.dtype == 'object':
        return list(series)
    else:
        # Return the original series if the data type doesn't match any condition
        return series


def collect_to_list(series):
    return list(series)


df = df.groupby(['group', 'isWholeHeader']).agg(custom_agg).reset_index()
df.sort_values(by='score', ascending=False, inplace=True, ignore_index=True)

print(df)

# Record with the max value in the 'score' column
max_score_record = df[df['score'] == df['score'].max()]

# Record with the max pair of values in 'meanCtxScore' and 'meanSeedScore'
max_pair_record = df[(df['meanCtxScore'] == df['meanCtxScore'].max()) & (df['meanSeedScore'] == df['meanSeedScore'].max())]

print("Record with max score:")
print(max_score_record)

print("\nRecord with max pair of values in meanCtxScore and meanSeedScore:")
print(max_pair_record)
