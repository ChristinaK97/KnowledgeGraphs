import math
import re
import string
from typing import Tuple, Set, List

from thefuzz import fuzz as fz

from UnionFind import UnionFind

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
    if leven >= 98:
        return True        
"""


def removeNearDuplicates(inputList: List[str]) -> List[str]:

    processed = [process(fullForm) for fullForm in inputList]
    # print(processed)

    if len(processed) == 1:
        return [processed[0][0]]

    nearDuplicatePairs = UnionFind(len(processed))

    for idx1 in range(len(processed)):
        _, split1, concat1 = processed[idx1]
        for idx2 in range(idx1 + 1, len(processed)):
            _, split2, concat2 = processed[idx2]

            if concat1 == concat2 or split1 == split2 or \
               (lenDiff(concat1, concat2) < LEN_DIFF_THRS and fz.ratio(concat1, concat2) >= LEVEN_THRS):
                nearDuplicatePairs.union(idx1, idx2)

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