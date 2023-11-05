from itertools import product
from os import makedirs
import pickle
from typing import Dict, List, Set, Tuple
from pytrie import SortedStringTrie as Trie
import pandas as pd
from os.path import exists
from shutil import rmtree

from tqdm import tqdm

from AAExpansion.source.HeadersDataset import WORD, UNK, ENTRY, TAG, SPAN
from AAExpansion.source.util.NearDuplicates import groupNearDuplicates


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

        self.datasetAbbrevDetected = set()

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
    def _readDictionaryCSV(self, dictionaryCSV: str, delimiter: str, maintainAbbrevEntries:bool = False) -> pd.DataFrame:
        # Read the dictionary
        # Create a list to store the new rows
        # Get unique values of 'SF' column
        # Iterate through unique SF values
        # Create a new row with the same SF and LF value
        # Append the new rows to the DataFrame
        df = pd.read_csv(dictionaryCSV, delimiter=delimiter, encoding='utf-8', low_memory=False)
        if maintainAbbrevEntries:
            maintainedAbbrevEntries = []
            for abbrev in df[self.abbrevCol].unique():
                maintainedAbbrevEntries.append({
                    self.abbrevCol: abbrev,
                    self.fullFormCol: abbrev,
                })
            maintainedAbbrevEntries = pd.DataFrame(maintainedAbbrevEntries)
            df = pd.concat([df, maintainedAbbrevEntries], ignore_index=True)
        return df


    def _loadTries(self, datasetAlphabet: Set[str] = None):
        return {
            letter : self._loadFromPickle(letterFile)
            for letter, letterFile in tqdm(self._loadFromPickle(self._getLetterDict()).items())
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


    def getDatasetAbbrevDetected(self):
        """Call after searching for candidates for all the headers to get a set of all the
           abbreviations found in the dataset. """
        return self.datasetAbbrevDetected

    def getExactMatch(self, abbrev, rmvIdentical:bool = True):
        firstLetter = abbrev[0]
        if firstLetter in self.letterTries:
            fullForm = self.letterTries[firstLetter].get(abbrev)
            if fullForm is not None:
                fullForm = set(fullForm.keys())
                if rmvIdentical: fullForm.discard(abbrev)
                return fullForm
            else:
                return []




    def generateHeaderCandidates(self, headerInfo):
        """
        :param headerInfo: header -> str, tokenizedHeader -> str, isUnambiguous (ignored) -> bool,
                           headerInputs ->  parallel lists entry, tag, span
        :return:
            partialAbbrevsDetected: Tuple[str] of all the abbreviations detect within the header sorted according to
                                    span. Excluding whole header abbrev
                                    e.g., (micro, alb)
            partialCands: Dict[Tuple[str], str],
                          key= the interpretation of each abbrev in the combination. The order corresponds with the
                               order of the abbrev in the partialAbbrevsDetected
                          value= The candidate interpretation of the combination
                          It contains all the possible interpretations of the header -> all the interpretations of
                          the abbreviation as found in the trie in case a single abbreviation is found in the header,
                          Or if more than one abbrev is found in the header, all the possible combinations of their interpretation,
                          including candidates where only a portion of the abbreviations present were replaced by
                          their interpretation. -> this happens if maintainAbbrevEntries==True when reading the csv
                          E.g., {
                                      ('microcytic', 'albendazole'):   microcytic albendazole,
                                      ('microcytic', 'albumin'):       microcytic albumin,
                                      ('microcytic', 'alb'):           microcytic alb, ...
                                }
            wHeaderCands: Dict[Tuple[str], str] same thing as the partialCands but for whole header as abbrev candidates
                          E.g., for header = 'MI'
                          	    {('myocardial infarction',) : myocardial infarction,
	                             ('massa intermedia',) : massa intermedia,...}

	        If no abbreviations and candidates were found, it returns None * 3
        """
        header, tokenizedHeader, _, headerInputs = headerInfo

        headerAbbrevCands = {}
        for entry, tag, span in zip(headerInputs[ENTRY], headerInputs[TAG], headerInputs[SPAN]):

            if tag == WORD:
                firstLetter = entry[0]
                if firstLetter in self.letterTries:
                    abbrev, fullForm = self.letterTries[firstLetter].longest_prefix_item(entry, (None, None))
                    if abbrev is not None and len(abbrev) == len(entry):
                        print(f"\tYS EXACT CANDS FOR '{entry}'  =   {abbrev}  :  {fullForm}")
                        self.datasetAbbrevDetected.add(abbrev)
                        headerAbbrevCands[entry] = (span, fullForm)

                    elif abbrev is not None:
                        print(f"\tNO EXACT CANDS FOR '{entry}' . CLOSEST CAND =  {abbrev}  :  {fullForm}")
                    else:
                        print(f"\tNO EXACT CANDS FOR '{entry}'")

            elif tag == UNK:
                pass  # TODO

        if headerAbbrevCands:
            return self._gen_partial_and_whole_header_cands(header, tokenizedHeader, headerAbbrevCands)
        return None, None, None


    def _gen_partial_and_whole_header_cands(self, header, tokenizedHeader, headerAbbrevCands):
        # handle whole header candidates separately
        wHeaderCands = headerAbbrevCands.pop(header, None)                                                              # ;print("\tCANDIDATES:")

        # partial header candidates generation
        partialAbbrevsDetected, partialCands = self._generateCandidates(tokenizedHeader, headerAbbrevCands)

        # whole header candidates generation
        if wHeaderCands is not None:
            wHeaderCands = {header : wHeaderCands}
            wHeaderCands = self._generateCandidates(tokenizedHeader, wHeaderCands)[1]
        return partialAbbrevsDetected, partialCands, wHeaderCands


    def _generateCandidates(self, tokenizedHeader, headerAbbrevCands):
        """
        Explanation for generating combinations:
        For generating interpretations by changing an abbreviation that is contained in a header
        and does not span across the whole header. Eg, 'ECG stress' -> 'electrocardiogram stress'
        Some headers might contain multiple abbreviations, so a candidate interpretation will be generated
        for each combination of the full forms of the abbreviations

        Eg.,    micro         alb   <- sortedBySpans
            ('microcytic', 'albendazole')   microcytic albendazole
            ('microcytic', 'albumin')       microcytic albumin
            ('microcytic', 'alb')           microcytic alb
            ('microscopy', 'albendazole')   microscopy albendazole
            ('microscopy', 'albumin')       microscopy albumin
            ('microscopy', 'alb')           microscopy alb
            ('micro', 'albendazole')        micro albendazole
            ('micro', 'albumin')            micro albumin
            The original for of each abbrev is also consider for the candidates as
            one of the abbrev might give a better score if it's not interpreted,
            but the original tokenizedHeader is excluded here. -> TODO should check during selection
        """
        candidates = {}
        spans, sortedBySpan, combinations = self._prepareCombinations(headerAbbrevCands)
        for combination in combinations:
            candidate = ''
            prevEnd = 0
            for dim, abbrev in enumerate(sortedBySpan):
                currStart = spans[abbrev][0]
                candidate += tokenizedHeader[prevEnd: currStart] + combination[dim]
                prevEnd = spans[abbrev][1]
            candidate += tokenizedHeader[prevEnd:]
            if candidate != tokenizedHeader:
                candidates[combination] = candidate                                                                     # ; print(f"\t{combination} : {candidate}")
        return tuple(sortedBySpan), candidates


    def _prepareCombinations(self, headerAbbrevCands):
        # Sort the dictionary keys based on the first element of each span
        # Create a list of dictionaries in the sorted order
        spans = {abbrev: headerAbbrevCands[abbrev][0] for abbrev in headerAbbrevCands.keys()}
        sortedBySpan = sorted(spans.keys(), key=lambda k: spans[k][0])
        abbrevDict = [headerAbbrevCands[abbrev][1] for abbrev in sortedBySpan]

        fullFormsMain = [d.keys() for d in abbrevDict]
        combinations = list(product(*fullFormsMain))
        return spans, sortedBySpan, combinations








