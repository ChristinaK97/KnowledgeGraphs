import pickle
import re
from os.path import exists

from wordninja import split as ninja
from util.NearDuplicates import hasDuplicateIn

# model_name = 'monologg/biobert_v1.1_pubmed'
# from transformers import BertTokenizer
# tokenizer = BertTokenizer.from_pretrained(model_name)

import nltk
from nltk.corpus import wordnet, stopwords


saveFile = "resources\\headerTokenizerOutput.pkl"

WORD = 1
NUM = 2
UNK = 3
STOPWORD = 4
UNDETER = -1

ENTRY = 0
TAG = 1
SPAN = 2

LINKER_THRS = 0.78


class HeaderTokenizer:


    def __init__(self, headers, resetTokenizer:bool = False):

        if resetTokenizer or not exists(saveFile):
            self._initLibraries()

            self.headers = headers
            self.tokenizedHeaders = []
            self.headersAlphabet = None
            self.ninjaHeaders = None
            self.separators = []
            self.tags = {}
            self.spans = []
            self.isUnambiguous = []
            self.headerInputs = []
            self.headersIdxs = range(len(self.headers))

            self._generateHeaderInputs()
            self._saveResults()
        else:
            self._loadResults()


    def getHeaderInputs(self):
        return self.headerInputs




    def _initLibraries(self):
        nltk.download("wordnet")
        nltk.download('stopwords')
        self.en_stopwords = stopwords.words('english')

        # DONT REMOVE THESE UNUSED IMPORTS
        import spacy
        import scispacy
        from scispacy.linking import EntityLinker

        self.nlp = spacy.load("en_core_sci_lg")
        self.nlp.add_pipe("scispacy_linker", config={"resolve_abbreviations": True, "linker_name": "umls"})
        self.linker = self.nlp.get_pipe("scispacy_linker")


    def _generateHeaderInputs(self):
        """ driver """
        self._createHeadersAlphabet()
        self._ninjaSpit()
        for idx in self.headersIdxs:
            self.separators.append(self._getSeparators(idx))
            self._repairSingleChar(idx)
            self._repairSplitedWords(idx)
            self._createHeaderTags(idx)
            self.isUnambiguous.append(self._isUnambiguous(idx))
            self._normalizeSeparators(idx)
            self.tokenizedHeaders.append(self._createTokenizedHeader(idx))
            self.spans.append(self._findSpans(idx))
            self.headerInputs.append(self._createHeaderInputs(idx))
        self.print_()



    def _createHeadersAlphabet(self):
        # Initialize an empty set to collect unique characters
        # Iterate through each string and add its characters to the set
        # Convert the set of unique characters back to a sorted list
        self.headersAlphabet = set()
        for header in self.headers:
            self.headersAlphabet.update(set(header))


    def _saveResults(self):
        attributes_to_save = {
            'headers': self.headers,
            'tokenizedHeaders': self.tokenizedHeaders,
            'headersAlphabet': self.headersAlphabet,
            'ninjaHeaders': self.ninjaHeaders,
            'separators': self.separators,
            'tags': self.tags,
            'spans': self.spans,
            'isUnambiguous': self.isUnambiguous,
            'headerInputs': self.headerInputs,
        }
        with open(saveFile, 'wb') as file:
            pickle.dump(attributes_to_save, file)


    def _loadResults(self):
        with open(saveFile, 'rb') as file:
            la = pickle.load(file)

        self.headers = la['headers']
        self.tokenizedHeaders = la['tokenizedHeaders']
        self.headersAlphabet = la['headersAlphabet']
        self.ninjaHeaders = la['ninjaHeaders']
        self.separators = la['separators']
        self.tags = la['tags']
        self.spans = la['spans']
        self.isUnambiguous = la['isUnambiguous']
        self.headerInputs = la['headerInputs']




    def _ninjaSpit(self):
        self.ninjaHeaders = [ninja(h) for h in self.headers]


    def _getSeparators(self, idx):
        header = self.headers[idx]
        ninjaHeader = self.ninjaHeaders[idx]

        seps = []
        sep = ''
        for i in range(len(ninjaHeader) - 1):
            token2 = ninjaHeader[i + 1]
            header = header[len(ninjaHeader[i]) + len(sep):]
            idxToken2 = header.index(token2)
            sep = header[:idxToken2]
            seps.append('' if idxToken2 == 0 else sep)
        return seps


    def _repairSingleChar(self, idx):
        ninjaHeader = self.ninjaHeaders[idx]
        seps = self.separators[idx]

        upNinjaHeader = []
        rmvIdxsSeps = set()
        rearUpdated = False

        def areSameCaseChars(char1, char2):
            return char1.isupper() == char2.isupper() or char1.islower() == char2.islower()

        def makeUpdate(condition):
            if condition:
                upNinjaHeader[-1] += token
            else:
                upNinjaHeader.append(token)

        for i in range(len(ninjaHeader)):
            token = ninjaHeader[i]

            if len(token) == 1 and token.isalpha():
                frontSep = seps[i - 1] if i > 0 else None
                rearSep = seps[i] if i < len(seps) else None

                if frontSep == '' and areSameCaseChars(ninjaHeader[i - 1][-1], token[0]):
                    makeUpdate(upNinjaHeader)
                    rearUpdated = False
                    rmvIdxsSeps.add(i-1)
                elif rearSep == '' and areSameCaseChars(ninjaHeader[i + 1][0], token[0]):
                    makeUpdate(rearUpdated)
                    rearUpdated = True
                    rmvIdxsSeps.add(i)
                else:
                    makeUpdate(rearUpdated)
                    rearUpdated = False
            else:
                makeUpdate(rearUpdated)
                rearUpdated = False

        self.ninjaHeaders[idx] = upNinjaHeader
        self.separators[idx] = [seps[i] for i in range(len(seps)) if i not in rmvIdxsSeps]




    def consecutive(self, data, stepsize=1):
        result = []
        current_sublist = []

        for item in data:
            if not current_sublist or item == current_sublist[-1] + stepsize:
                current_sublist.append(item)
            else:
                result.append(current_sublist)
                current_sublist = [item]

        if current_sublist:
            result.append(current_sublist)

        return result


    def _repairSplitedWords(self, idx):
        weakDelim = {'', '-'}
        ninjaHeader = self.ninjaHeaders[idx]

        seps = self.separators[idx]
        emptySepIdxs = self.consecutive([i for i in range(len(seps)) if seps[i] in weakDelim])
                                                                                                                                    # print(ninjaHeader); print(emptySepIdxs)
        sepIdxRmv = set()
        replacements = {}
        for idxGroup in emptySepIdxs:
            idxGroup.append(idxGroup[-1]+1)
            if len(idxGroup)==2 and (self._isNumeric(ninjaHeader[idxGroup[0]]) or self._isNumeric(ninjaHeader[idxGroup[-1]])):
                continue
            possibleWord = ''.join(ninjaHeader[i] + (seps[i] if i<len(idxGroup)-1 else '') for i in idxGroup)                       # ;print(possibleWord)
            if re.search(r'\D\d+(\.\d+)?\D', possibleWord):
                continue

            isEnWord, tag = self._isaEnglishWord(possibleWord)
            if isEnWord:
                sepIdxRmv.update(idxGroup[:-1])
                for i in idxGroup: replacements[i] = (possibleWord, tag)

        if sepIdxRmv:
            self.separators[idx] = [seps[i] for i in range(len(seps)) if i not in sepIdxRmv]
            upNinjaHeader = []
            headerTags = []

            lastIdx = -1
            for i in range(len(ninjaHeader)):
                if i not in replacements:
                    upNinjaHeader.append(ninjaHeader[i])
                    headerTags.append(UNDETER)
                elif lastIdx == i-1:
                    upNinjaHeader.append(replacements[i][0])
                    headerTags.append(replacements[i][1])

            assert len(headerTags) == len(upNinjaHeader)
            self.ninjaHeaders[idx] = upNinjaHeader
            self.tags[idx]   = headerTags

        # print("---------")


    def _isNumeric(self, word):
        return re.match(r'^\d+(\.\d+)?$', word)


    def _isaEnglishWord(self, word, checkLinker=True):
        if self._isNumeric(word):
            return True, NUM

        if len(word) == 1 or word.lower() in self.en_stopwords:
            return True, STOPWORD

        if wordnet.synsets(word):
            return True, WORD

        if checkLinker:
            entities = self.nlp(word).ents
            if entities:
                matches = entities[0]._.kb_ents
                if matches and matches[0][1] > LINKER_THRS:
                    return self._checkMatches(word, matches)

            return False, UNK


    def _checkMatches(self, word, matches):
        for umls_ent, score in matches:
            info = self.linker.kb.cui_to_entity[umls_ent]                                               # print(f"\nEntity = {umls_ent}  _  Score = {score}"); print(info)
            if hasDuplicateIn(word, info.aliases + [info.canonical_name]):
                return True, WORD
        return False, UNK



    def _createHeaderTags(self, idx):
        ninjaHeaders = self.ninjaHeaders[idx]
        headerTags = self.tags.get(idx, [])
        wasEmpty = len(headerTags) == 0

        for i, word in enumerate(ninjaHeaders):
            if wasEmpty or headerTags[i] == UNDETER:

                _, tag = self._isaEnglishWord(word)

                if wasEmpty: headerTags.append(tag)
                else: headerTags[i] = tag

        self.tags[idx] = headerTags


    def _isUnambiguous(self, idx):
        return UNK not in set(self.tags[idx])


    def _normalizeSeparators(self, idx):
        self.separators[idx] = [sep if sep not in {'','_'} else ' ' for sep in self.separators[idx]]


    def _createTokenizedHeader(self, idx):
        ninjaHeader = self.ninjaHeaders[idx]
        seps = self.separators[idx]
        lH = len(ninjaHeader)
        return ''.join(ninjaHeader[i] + (seps[i] if i < lH - 1 else '') for i in range(lH))



    def _findSpans(self, idx):
        ninjaHeader = self.ninjaHeaders[idx]
        seps = self.separators[idx]

        spans = []
        start = 0
        for i, nh in enumerate(ninjaHeader):
            token = ninjaHeader[i]
            end = start + len(token)
            spans.append((start, end))
            try:
                start = end + len(seps[i])
            except IndexError:
                pass    # for last token
        return spans


    def _createHeaderInputs(self, idx):
        ninjaHeader = self.ninjaHeaders[idx]
        spans = self.spans[idx]
        tags = self.tags[idx]
        seps = self.separators[idx]

        def addInput(entr, tag, span):
            if entr not in _addedEntries:
                inputEntries.append(entr)
                inputTags.append(tag)
                inputSpans.append(span)
                _addedEntries.add(entr)


        # the whole header as entry
        inputEntries  = [self.headers[idx]]
        inputTags = [WORD]
        inputSpans = [(0, len(inputEntries[0]))]
        _addedEntries = {inputEntries[0]}


        if len(ninjaHeader) > 1:
            for i, token in enumerate(ninjaHeader):
                tokenSpan, tokenTag = spans[i], tags[i]

                if tokenTag == WORD:
                    addInput(token, tokenTag, tokenSpan)

                elif tokenTag == NUM:
                    if i > 0 and tags[i - 1] != STOPWORD:
                        entry = f"{ninjaHeader[i - 1]}{seps[i - 1]}{token}"
                        start = spans[i-1][0]
                        end = tokenSpan[1]
                        addInput(entry, WORD, (start, end))


                    if i < len(seps) and tags[i + 1] != STOPWORD:
                            entry = f"{token}{seps[i]}{ninjaHeader[i + 1]}"
                            start = tokenSpan[0]
                            end = spans[i + 1][1]
                            addInput(entry, WORD, (start, end))

                elif tokenTag == UNK:
                    pass  # TODO

                elif tokenTag == STOPWORD:
                    pass  # don't try to interpreter stopwords

        return inputEntries, inputTags, inputSpans




    def print_(self):
        for idx, (h, nh, th, sep, sn, iu, hi) in enumerate(zip(self.headers, self.ninjaHeaders, self.tokenizedHeaders, self.separators, self.isUnambiguous, self.spans, self.headerInputs)):
            print(f">> '{h}'  ->  {nh}  ->  '{th}' \t {sep} \t {self.tags.get(idx, [])}  ->  {iu} \t {sn} \t {hi}")
        print("="*30)

    def printHeaderInfo(self, idx):
        print(f">> '{self.headers[idx]}'  ->  {self.ninjaHeaders[idx]}  ->  '{self.tokenizedHeaders[idx]}' \t {self.separators[idx]} \t {self.tags.get(idx, [])}  ->  {self.isUnambiguous[idx]} \t {self.spans[idx]} \t {self.headerInputs[idx]}")




"""
        def _wordRecognition(self, header, ninjaHeader, seps):
        print(f">> {header}  {ninjaHeader}")

        if self._isaEnglishWord(header, len(ninjaHeader), seps)[0]:
            return [header]

        words = []
        for i, w in enumerate(ninjaHeader) :
            isEnWord, isDigit = self._isaEnglishWord(w, 1)

            if isDigit:
                if i>0 and seps[i-1] != ' ':
                    words.append(f"{ninjaHeader[i-1]}{seps[i-1]}{w}")
                elif i<len(seps) and seps[i] != ' ':
                    words.append(f"{w}{seps[i]}{ninjaHeader[i+1]}")
            elif isEnWord:
                words.append(w)
    """

"""def normalizeSeparators(self, idx):
    ninjaHeader = self.ninjaHeaders[idx]
    seps = self.separators[idx]
    # tags = self.tags[idx]
    lH = len(ninjaHeader)

    normalized = ''
    for i in range(lH):
        if i < lH - 1:
            sep = seps[i] if seps[i] not in {'', '_'} else ' '
        else:
            sep = ''
        normalized += ninjaHeader[i] + sep

    return normalized"""

"""
l, s = ['NA', 's', 't'], ['']*2
assert repairSingleChar(l, s) == ['NA', 'st']

l, s = ['Na', 's', 't', 'end'], ['']*3
assert repairSingleChar(l, s) == ['Nast', 'end']
"""