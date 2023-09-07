
from MedicalDictionary import MedicalDictionary
from util.NearDuplicates import groupNearDuplicates

path = "C:\\Users\\karal\\OneDrive\\Υπολογιστής\\clinical-abbreviations-1.0.2\\clinical-abbreviations-1.0.2" \
       "\\metainventory\\Metainventory_Version1.0.0.csv"
# medDict = MedicalDictionary(dictionaryCSVPath=path, printTries=True, resetTries=False)

s = [
'angiotensinconverting enzyme inhibitor',
'angiotensin converting enzyme inhibitor',
'angiotensin converting-enzyme inhibitor',
'angiotensin-converting enzyme inhibitor',
'angiotensin-converting-enzyme inhibitor',
'angiotensin converting enzyme inhibition',
'angiotensin-converting enzyme inhibition',
'angiotensin-converting-enzyme inhibition',
'angiotensinconverting enzyme inhibitor',
'angiotensin converting enzyme inhibitor',
'angiotensin converting-enzyme inhibitor',
'angiotensin-converting enzyme inhibitor',
'angiotensin-converting-enzyme inhibitor',
'angiotensin converting enzyme inhibition',
'angiotensin-converting enzyme inhibition',
'angiotensin-converting-enzyme inhibition',
'angiotensin converting enzyme',
'angiotensin converting enzyme'
]
r = groupNearDuplicates(s)
print(r)