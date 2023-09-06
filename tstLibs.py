import pandas as pd
from spacy.tokens import Doc, Span

from MedicalDictionary import MedicalDictionary

path = "C:\\Users\\karal\\OneDrive\\Υπολογιστής\\clinical-abbreviations-1.0.2\\clinical-abbreviations-1.0.2" \
       "\\metainventory\\Metainventory_Version1.0.0.csv"
inputDataset = "Data_test_Encrypt_Repaired.csv"

headers = pd.read_csv(inputDataset, delimiter=";").columns.to_list()
# ninjaHeaders = ninjaSpit(headers)

"""
d = MedicalDictionary(path)
for h, nh in zip(headers, ninjaHeaders):
    print(">> ", h, "  ", nh)
    d.generateCandidates(h)
    print("===============")
"""

"""
import spacy
import scispacy
from scispacy.linking import EntityLinker

nlp = spacy.load("en_core_sci_lg")
nlp.add_pipe("scispacy_linker", config={"resolve_abbreviations": True, "linker_name": "umls"})
linker = nlp.get_pipe("scispacy_linker")

for h in ninjaHeaders:
    h = " ".join(h)
    doc = nlp(h)
    print(">> ", h)
    for ent in doc.ents:
        print(ent)
        for umls_ent in ent._.kb_ents:
            print(linker.kb.cui_to_entity[umls_ent[0]])
    print("--------")
"""

import spacy
import scispacy
from scispacy.linking import EntityLinker

nlp = spacy.load("en_core_sci_lg")
nlp.add_pipe("scispacy_linker", config={"resolve_abbreviations": True, "linker_name": "umls"})
linker = nlp.get_pipe("scispacy_linker")

for h in headers:
    doc = nlp(h)
    print(">> ", h)
    for ent in doc.ents:
        matches = ent._.kb_ents
        for umls_ent, score in matches:
            print(f"Entity = {umls_ent}  _  Score = {score}")
            info = linker.kb.cui_to_entity[umls_ent]
            print(info)
    print("--------")

"""
import scispacy
import spacy
import en_core_sci_lg

nlp = spacy.load("en_core_sci_lg")

for h, nh in zip(headers, ninjaHeaders):
    h_ents = nlp(h).ents
    nh_ents = nlp(' '.join(nh)).ents
    print(f'''
        >> {h} = {[(e.text, e.label_) for e in h_ents]}
           {nh} = {[(e.text, e.label_) for e in nh_ents]}
    ''')
"""

"""
import spacy

nlp = spacy.load("en_core_sci_lg")
doc = nlp("Apple is looking at buying U.K. startup for $1 billion")

for h, nh in zip(headers, ninjaHeaders):
    print(">> ", h)
    doc = nlp(h)
    for token in doc:
        print(token.text, token.lemma_, token.pos_, token.tag_, token.dep_,
                token.shape_, token.is_alpha, token.is_stop)

    print(">> ", nh)
    doc = nlp(' '. join(nh))
    for token in doc:
        print(token.text, token.lemma_, token.pos_, token.tag_, token.dep_,
              token.shape_, token.is_alpha, token.is_stop)

    print("======")
"""

"""
import spacy
from scispacy.abbreviation import AbbreviationDetector

nlp = spacy.load("en_core_sci_lg")
nlp.add_pipe("abbreviation_detector")

for h in ["Spinal and bulbar muscular atrophy (SBMA) is an inherited motor neuron disease"]:
    print(f">> {h}")
    doc = nlp(h)
    doc.set_extension("abbreviations", default=[], force=True)
    for abrv in doc._.abbrevations:
        print(f"{abrv} \t ({abrv.start}, {abrv.end}) {abrv._.long_form}")
    print("===============")
"""
