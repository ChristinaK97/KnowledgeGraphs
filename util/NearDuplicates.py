import math
import re
import string
from typing import Tuple, Set, List, Dict

from thefuzz import fuzz as fz

from util.UnionFind import UnionFind

LEN_DIFF_THRS = 15
LEVEN_THRS = 95


def lenDiff(x: str, y: str):
    lx = len(x)
    ly = len(y)
    return (abs(lx - ly) / ((lx + ly) / 2)) * 100


def process(fullForm: str) -> Tuple[str, Set[str], str]:
    noPunct = fullForm.translate(str.maketrans(string.punctuation, ' ' * len(string.punctuation)))
    split = re.findall(r"\b\w+\b", noPunct.lower())
    concat = "".join(split)
    return noPunct, set(split), concat


def hasDuplicateIn(word, candAnnotations):
    _, wSplit, wConcat  = process(word)
    processed = [process(el) for el in candAnnotations]

    for _, cSplit, cConcat in processed:
        if wConcat == cConcat or wSplit == cSplit or \
            (lenDiff(wConcat, cConcat) < LEN_DIFF_THRS and fz.ratio(wConcat, cConcat) >= LEVEN_THRS):
            return True

    return False


"""
if wConcat == cConcat or wSplit == cSplit :
    print(f"Identical \t< {wConcat} \t {cConcat} >")
    return True

elif lenDiff(wConcat, cConcat) < LEN_DIFF_THRS:
    leven = fz.ratio(wConcat, cConcat)
    print(f"{leven} {lenDiff(wConcat, cConcat)}\t< {wConcat} \t {cConcat} >")
    if leven >= LEVEN_THRS:
        return True        
"""




def groupNearDuplicates(inputList: List[str]) -> Dict[str, List[str]]:

    processed = [process(fullForm) for fullForm in inputList]
    # print(processed)

    if len(processed) == 1:
        return {processed[0][0] : [processed[0][0]]}

    nearDuplicates = UnionFind(len(processed))

    for idx1 in range(len(processed)):
        _, split1, concat1 = processed[idx1]
        for idx2 in range(idx1 + 1, len(processed)):
            _, split2, concat2 = processed[idx2]

            if concat1 == concat2 or split1 == split2 or \
               (lenDiff(concat1, concat2) < LEN_DIFF_THRS and fz.ratio(concat1, concat2) >= LEVEN_THRS):
                nearDuplicates.union(idx1, idx2)

    nearDuplicateSets = nearDuplicates.getSets()
    # print(nearDuplicateSets)
    # =====================================================================

    distinctAnnots = {}
    for dSet in nearDuplicateSets:
        minLen, maxWhite, minAnnot, altAnnots = math.inf, -1, None, set()
        for idx in dSet:
            annot = processed[idx][0]
            altAnnots.add(annot)

            n_white = len(annot) - len(processed[idx][2])
            cLen = len(annot) - n_white

            if cLen < minLen or (cLen == minLen and n_white > maxWhite):
                minLen, maxWhite, minAnnot = cLen, n_white, annot

        distinctAnnots[minAnnot] = list(altAnnots)

    # print(distinctAnnots)
    # print("="*30)
    # return split_key_based_on_values(distinctAnnots)
    return distinctAnnots


"""
if concat1 == concat2 or split1 == split2 :
    print(f"Identical \t< {inputList[idx1]} \t {inputList[idx2]} >")
    nearDuplicatePairs.union(idx1, idx2)

elif lenDiff(concat1, concat2) < LEN_DIFF_THRS:
    leven = fz.ratio(concat1, concat2)
    if leven >= LEVEN_THRS:
        print(f"{leven} {lenDiff(concat1, concat2)}\t< {concat1} \t {concat2} >")
        nearDuplicatePairs.union(idx1, idx2)
else:
    print(f"Diff \t< {inputList[idx1]} \t {inputList[idx2]} >")            
"""



# ===============================================================================================================
"""
def split_key_based_on_values(dictionary):
    new_dict = {}
    for key, values in dictionary.items():
        substr = common_substring(values)
        if substr:
            parts = key.split(substr)
            if len(parts) == 2:
                new_key = f'{parts[0]} {substr} {parts[1]}'.strip()
            else:
                new_key = key
            new_dict[new_key] = values
        else:
            new_dict[key] = values
    return new_dict

def common_substring(strings):
    substr = ""
    if not strings:
        return substr
    min_len = min(len(s) for s in strings)
    for i in range(min_len):
        if all(s[i] == strings[0][i] for s in strings):
            substr += strings[0][i]
        else:
            break
    return substr
"""

