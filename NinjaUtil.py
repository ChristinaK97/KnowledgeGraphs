import pandas as pd
import wordninja
from wordninja import split as ninja


from transformers import BertTokenizer
model_name = 'monologg/biobert_v1.1_pubmed'
# tokenizer = BertTokenizer.from_pretrained(model_name)


def _getSeparators(header, ninjaHeader):
    seps = []
    sep = ''
    for i in range(len(ninjaHeader)-1):
        token2 = ninjaHeader[i+1]
        header = header[len(ninjaHeader[i]) + len(sep):]
        idxToken2 = header.index(token2)
        sep = header[:idxToken2]
        seps.append('' if idxToken2 == 0 else sep)
    return seps


def _repairSingleChar(ninjaHeader, seps):
    upNinjaHeader = []
    rearUpdated = False

    def areSameCaseChars(char1, char2):
        return char1.isupper() == char2.isupper() or char1.islower() == char2.islower()
    def makeUpdate(condition):
        if condition: upNinjaHeader[-1] += token
        else: upNinjaHeader.append(token)

    for i in range(len(ninjaHeader)):
        token = ninjaHeader[i]

        if len(token)==1 and token.isalpha():
            frontSep = seps[i-1] if i>0 else None
            rearSep = seps[i] if i<len(seps) else None

            if frontSep=='' and areSameCaseChars(ninjaHeader[i-1][-1], token[0]):
                makeUpdate(upNinjaHeader)
                rearUpdated = False
            elif rearSep=='' and areSameCaseChars(ninjaHeader[i+1][0], token[0]):
                makeUpdate(rearUpdated)
                rearUpdated = True
            else:
                makeUpdate(rearUpdated)
                rearUpdated = False
        else:
            makeUpdate(rearUpdated)
            rearUpdated = False

    return upNinjaHeader


def ninjaSpit(headers):
    ninjaHeaders = [ninja(h) for h in headers]
    separators = [_getSeparators(h, nh) for h, nh in zip(headers, ninjaHeaders)]
    upNinjaHeaders = [_repairSingleChar(nh, seps) for nh, seps in zip(ninjaHeaders, separators)]

    # [print(f"{h}\t{nh}\t{seps}\t{upnh}") for h, nh, seps, upnh in zip(headers, ninjaHeaders, separators, upNinjaHeaders)]
    return upNinjaHeaders

"""
l, s = ['NA', 's', 't'], ['']*2
assert repairSingleChar(l, s) == ['NA', 'st']

l, s = ['Na', 's', 't', 'end'], ['']*3
assert repairSingleChar(l, s) == ['Nast', 'end']
"""