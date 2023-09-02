import math
from os import makedirs
import pickle
import re
import string
from typing import Dict, List, Set, Tuple
from pytrie import SortedStringTrie as Trie
import pandas as pd
from thefuzz import fuzz as fz
from os.path import exists
from shutil import rmtree

from UnionFind import UnionFind


def removeNearDuplicates(inputList: List[str]) -> List[str]:
    def lenDiff(x:str, y:str):
        lx = len(x)
        ly = len(y)
        return (abs(lx - ly) / ((lx + ly) / 2)) * 100

    def process(fullForm:str) -> Tuple[str, Set[str], str]:
        noPunct = fullForm.translate(str.maketrans(string.punctuation, ' ' * len(string.punctuation)))
        split = re.findall(r"\b\w+\b", noPunct.lower())
        concat = "".join(split)
        return noPunct, set(split), concat

    processed = [process(fullForm) for fullForm in inputList]
    # print(processed)

    if len(processed) == 1:
        return [processed[0][0]]

    LEN_DIFF_THRS = 15
    LEVEN_THRS = 96

    nearDuplicatePairs = UnionFind(len(processed))

    for idx1 in range(len(processed)):
        _, split1, concat1 = processed[idx1]
        for idx2 in range(idx1 + 1, len(processed)):
            _, split2, concat2 = processed[idx2]

            if concat1 == concat2 or split1 == split2 or \
               (lenDiff(concat1, concat2) < LEN_DIFF_THRS and fz.ratio(concat1, concat2) >= LEVEN_THRS):
                nearDuplicatePairs.union(idx1, idx2)

            """
            if concat1 == concat2 or split1 == split2 :
                print(f"Identical \t< {inputList[idx1]} \t {inputList[idx2]} >")
                nearDuplicatePairs.union(idx1, idx2)

            elif lenDiff(concat1, concat2) < 15:
                leven = fz.ratio(concat1, concat2)
                if leven >= 98:
                    print(f"{leven} {lenDiff(concat1, concat2)}\t< {concat1} \t {concat2} >")
                    nearDuplicatePairs.union(idx1, idx2)
            else:
                print(f"Diff \t< {inputList[idx1]} \t {inputList[idx2]} >")            
            """

    nearDuplicateSets = nearDuplicatePairs.getSets()
    # print(nearDuplicateSets)

    distinctAnnots = []
    for dSet in nearDuplicateSets:
        minLen, minAnnot = math.inf, None
        for idx in dSet:
            currLen = len(processed[idx][0])
            if currLen < minLen:
                minLen, minAnnot = currLen, processed[idx][0]

        distinctAnnots.append(minAnnot)

    # print(distinctAnnots)
    # print("========================================")
    return distinctAnnots


class MedicalDictionary:

    BASE_LETTER_TRIES_DIR = 'letterTries\\'

    def __init__(self,
                 dictionaryCSVPath: str,
                 delimiter: str = "|",
                 abbrevCol:str = "SF",
                 fullFormCol:str = "LF",
                 resetTries:bool = False,
                 printTries:bool = True
    ):
        self.abbrevCol = abbrevCol
        self.fullFormCol = fullFormCol

        if resetTries or not self._saveFound():
            print("Create Tries")
            self._prepareFolder()
            self.letterTries = \
                self._createLetterTries(
                    self._makeLetterBuckets(
                        self._readDictionaryCSV(dictionaryCSVPath, delimiter)))
        else:
            print("Load tries")
            self.letterTries = self._loadAllTries()

        if printTries: self.printLetterTries()



    def _makeLetterBuckets(self, dictionaryDF:pd.DataFrame) -> Dict[str,Dict[str, List]]:
        """
        1. Group by abbreviation and aggregate full form values into a list of unique values
        2. Convert the arrays of unique values to lists
        3. Iterate over unique first letters in the abbreviation column
            4. Filter the DataFrame for rows with the current first letter
               Store the filtered DataFrame as a dictionary in the dictionary with the first letter as the key
        """
        # 1
        dictionaryDF = dictionaryDF.groupby(self.abbrevCol)[self.fullFormCol].unique().reset_index()
        # 2
        dictionaryDF[self.fullFormCol] = dictionaryDF[self.fullFormCol].apply(list).apply(removeNearDuplicates)
        # 3
        letterBuckets = {}
        unique_first_letters = dictionaryDF[self.abbrevCol].str[0].unique()
        for first_letter in unique_first_letters:
            # 4
            letterBuckets[first_letter] = dictionaryDF[dictionaryDF[self.abbrevCol].str[0] == first_letter] \
                                            .set_index(self.abbrevCol)[self.fullFormCol].to_dict()
        return letterBuckets


    def _createLetterTries(self, letterBuckets: Dict[str,Dict[str, List]]) -> Dict[str, Trie]:
        """
        :param letterBuckets: key = a character -> the first letter of all abbreviations in the dict value,
                              value = dict where
                                        key = an abbreviation
                                        value = a list with all unique interpretations of the abbreviation
        Create Files Dict where
                    key = a character -> the first letter of all abbreviations in the trie
                    value = a Trie where
                                    key = an abbreviation
                                    value = a list with all unique interpretations of the abbreviation
                Example:
                letterTrie[μ] value is:
                    SortedStringTrie({
                        'μ': ['micrometer', 'micrometre'],
                        'μ H chain': ['mu heavy chain', 'μ heavy chain'],
                        'μ OR': ['mu opioid receptor', 'μ opioid receptor'],
                        'μ-H chain': ['mu heavy chain', 'μ heavy chain'], ...})
        """

        letterTries = {}
        letterFilesDict = {}
        for letter, letterBucket in letterBuckets.items():

            letterTrie = Trie(letterBucket)
            letterTries[letter] = letterTrie

            letterFile = self._getLetterTrieFile(hash(letter))
            letterFilesDict[letter] = letterFile
            self._saveToPickle(letterTrie, letterFile)

        self._saveToPickle(letterFilesDict, self._getLetterDict())
        return letterTries



    def _readDictionaryCSV(self, dictionaryCSV: str, delimiter: str) -> pd.DataFrame:
        return pd.read_csv(dictionaryCSV, delimiter=delimiter, encoding='utf-8', low_memory=False)


    def _loadAllTries(self):
        return {
            letter : self._loadFromPickle(letterFile)
            for letter, letterFile in self._loadFromPickle(self._getLetterDict()).items()
        }


    def _loadFromPickle(self, file):
        try:
            with open(file, 'rb') as file:
                return pickle.load(file)
        except FileNotFoundError:
            print(f"File '{file}' not found. Skipping.")



    def _saveFound(self):
        return exists(MedicalDictionary.BASE_LETTER_TRIES_DIR) and \
                exists(self._getLetterDict())

    def _prepareFolder(self):
        # Check if the folder exists remove the folder and its contents
        bDir = MedicalDictionary.BASE_LETTER_TRIES_DIR
        if exists(bDir):
            rmtree(bDir)
        makedirs(bDir)

    def _getLetterDict(self):
        return f"{MedicalDictionary.BASE_LETTER_TRIES_DIR}LetterDict.pkl"

    def _getLetterTrieFile(self, letterFileName):
        return f"{MedicalDictionary.BASE_LETTER_TRIES_DIR}Trie{letterFileName}.pkl"

    def _saveToPickle(self, content, file):
        with open(file, 'wb') as f:
            pickle.dump(content, f)


    def printLetterTries(self):
        for letter, letterTrie in self.letterTries.items():
            print(f">> {letter} LETTER TRIE:\n{letterTrie}\n", "="*30)
        print(f"# tries = {len(self.letterTries)}\n{self.letterTries.keys()}")




path = "C:\\Users\\karal\\OneDrive\\Υπολογιστής\\clinical-abbreviations-1.0.2\\clinical-abbreviations-1.0.2" \
       "\\metainventory\\Metainventory_Version1.0.0.csv "
MedicalDictionary(path)
















