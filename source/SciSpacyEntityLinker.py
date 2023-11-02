import json

import nltk
from nltk.corpus import stopwords


class SciSpacyEntityLinker:
    def __init__(self, outputPath: str = None, results = None):

        self._readResults(outputPath, results)
        self._initLibraries()
        self._parseResults()


    def _readResults(self, outputPath, results):
        if results is not None:
            self.results = results
        elif results is None and outputPath is not None:
            with open(outputPath, 'r') as json_file:
                self.results = json.load(json_file)
        self.results = {
            header : {
                'tokenized' : headerInfo['tokenized'],
                'headerFFs' : set(cand['headerFF'] for cand in headerInfo['headerCands'])
            } for header, headerInfo in self.results.items()
        }

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



    def _parseResults(self):
        for header, headerInfo in self.results.items():
            print(f"\n>> HEADER {header}")
            tokenized, headerFFs = headerInfo['tokenized'], headerInfo['headerFFs']
            toLook = {header, tokenized}
            toLook.update(headerFFs)

            for h in toLook:
                doc = self.nlp(h)
                print("> ", h)
                for ent in doc.ents:
                    matches = ent._.kb_ents
                    for umls_ent, score in matches:
                        print(f"\nEntity = {umls_ent}  \t  Score = {score}")
                        info = self.linker.kb.cui_to_entity[umls_ent]
                        print(info)
                print("-"*50)
            print("="*50)












