import re

import numpy as np

s = "a"
s = s[1:]
print(s)

print("1.1".isnumeric())

def addone(innerL):
    innerL = [el + 1 for el in innerL]


l = [[0,0], [1,1]]
for iL in l:
    addone(iL)

print(l)

def consecutive(data, stepsize=1):
	return np.split(data, np.where(np.diff(data) != stepsize)[0]+1)

a = np.array([7, 1, 2, 3, 5, 4, 5, 6, 7, 9, 10])
consecutive(a)

possibleWord = "ICA1date"
print(re.search(r'\D\d+(\.\d+)?\D', possibleWord))

s = set()
if not s:
    print("Empty")
s.add(1)
l = [2,3,4]
s.update(l[:-1])
print(s)

d = {}


