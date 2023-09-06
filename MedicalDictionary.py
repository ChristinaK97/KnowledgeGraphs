from os import makedirs
import pickle
from typing import Dict, List, Set
from pytrie import SortedStringTrie as Trie
import pandas as pd
from os.path import exists
from shutil import rmtree

from HeaderTokenizer import WORD, UNK, ENTRY, TAG, SPAN
from util.NearDuplicates import groupNearDuplicates


class MedicalDictionary:

    BASE_LETTER_TRIES_DIR = 'resources\\letterTries\\'

    def __init__(self,
                 dictionaryCSVPath: str,
                 delimiter: str = "|",
                 abbrevCol:str = "SF",
                 fullFormCol:str = "LF",
                 datasetAlphabet: Set[str] = None,
                 resetTries:bool = False,
                 printTries:bool = False
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
            self.letterTries = self._loadTries(datasetAlphabet)
            print(f"Loaded {len(self.letterTries)} tries for letters = {self.letterTries.keys()}.\n")

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
        dictionaryDF[self.fullFormCol] = dictionaryDF[self.fullFormCol].apply(list).apply(groupNearDuplicates)
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
                                        value = a dictionary with all unique interpretations of the abbreviation
                                                (interpretations are grouped by searching for near duplicates)
        Create Files Dict where
                    key = a character -> the first letter of all abbreviations in the trie
                    value = a Trie where
                                    key = an abbreviation
                                    value = a dictionary with all unique interpretations of the abbreviation
                                            (interpretations are grouped by searching for near duplicates)
                Example:
                letterTrie[ω] value is:
                    SortedStringTrie({
                        'ω 3 FA': {
                            'omega 3 fatty acid': ['omega 3 fatty acid', 'omega3 fatty acid'],
                            'ω 3 fatty acid': ['ω 3 fatty acid', 'ω3 fatty acid']
                        },
                        'ω 3 PUFA': {
                            'omega 3 polyunsaturated fatty acid': ['omega 3 polyunsaturated fatty acid', 'omega3 polyunsaturated fatty acid']
                        }, ...
                    })
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


# ======================================================================================================================
    def _readDictionaryCSV(self, dictionaryCSV: str, delimiter: str) -> pd.DataFrame:
        return pd.read_csv(dictionaryCSV, delimiter=delimiter, encoding='utf-8', low_memory=False)

    def _loadTries(self, datasetAlphabet: Set[str] = None):
        return {
            letter : self._loadFromPickle(letterFile)
            for letter, letterFile in self._loadFromPickle(self._getLetterDict()).items()
            if datasetAlphabet is None or letter in datasetAlphabet
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

# ======================================================================================================================

    def generateAllPossibleCandidates(self, header):

        while header != '':
            firstLetter = header[0]
            if firstLetter in self.letterTries:
                match = self.letterTries[firstLetter].longest_prefix_item(header, None)
                print(header, match)
            header = header[1:]


    def generateCandidates(self, header, headerInputs):

        for entry, tag, span in zip(headerInputs[ENTRY], headerInputs[TAG], headerInputs[SPAN]):

            if tag == WORD:
                firstLetter = entry[0]
                if firstLetter in self.letterTries:
                    abbrev, fullForm = self.letterTries[firstLetter].longest_prefix_item(entry, (None, None))
                    if abbrev is not None and len(abbrev) == len(entry):
                        print(f"\tYS EXACT CANDS FOR '{entry}'  =   {abbrev}  :  {fullForm}")
                    elif abbrev is not None:
                        print(f"\tNO EXACT CANDS FOR '{entry}' . CLOSEST CAND =  {abbrev}  :  {fullForm}")
                    else:
                        print(f"\tNO EXACT CANDS FOR '{entry}'")

            elif tag == UNK:
                pass  # TODO


















