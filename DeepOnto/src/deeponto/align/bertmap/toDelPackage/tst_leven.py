import itertools
import math
import re
from itertools import product

from thefuzz import fuzz
from thefuzz import process


def tleven(s1, s2):
    print(fuzz.ratio(s1, s2))
    print(fuzz.partial_ratio(s1, s2))
    print(fuzz.token_sort_ratio(s1, s2))
    print(fuzz.token_set_ratio(s1, s2))
    print(fuzz.partial_token_sort_ratio(s1, s2))
    print()


def leven(src_annotations, tgt_annotations):
    return max([fuzz.token_sort_ratio(s_ann, t_ann) for s_ann, t_ann in product(src_annotations, tgt_annotations)])


src = "account person type"
tgt1 = "demand deposit account"
tgt2 = "person"
tgt3 = "interest rate"
for s, t in itertools.product([src], [tgt1, tgt2, tgt3]):
    pass  # tleven(s, t)


def sort_scores(scores):
    def compare_candidates(candidate):
        score, length = candidate[1], candidate[2]
        return score, length

    sorted_scores = sorted(scores, key=compare_candidates, reverse=False)
    return sorted_scores


def rank_candidates(src_annots, tgt_candidates):
    def score_scr_tgt_pair(tgt, src_annots, tgt_annots):
        candidate_scores = []
        for src_annot, tgt_annot in itertools.product(src_annots, tgt_annots):
            tgt_tokens = re.findall(r'\w+', tgt_annot)
            pair_score = [tgt, len(tgt_tokens), len(tgt_tokens)]
            for token in tgt_tokens:
                partial_score = fuzz.partial_ratio(token, src_annot)
                if partial_score == 100:
                    pair_score[1] -= 1

            candidate_scores.append(pair_score)
        final_candidate_score = sort_scores(candidate_scores)[0]
        return final_candidate_score

    tgt = ""
    final_candidates_scores = [score_scr_tgt_pair(tgt, src_annots, tgt_annots) for tgt_annots in tgt_candidates]
    final_candidates_scores = sort_scores(final_candidates_scores)


src_annots = ['contribution interest rate']
tgt_annots = [['rate'], ['interest rate'], ['something else with interest rate'], ['base interest']]

# rank_candidates(src_annots, tgt_annots)

l = [[4, 1, 2], [6, 1, 1], [11, 1, 1], [2, 2, 3], [1, 2, 3], [8, 2, 2], [10, 2, 2], [7, 3, 3], [5, 3, 3], [0, 5, 5]]
print(sort_scores(l))

best_rank = math.inf
best_bert_score = 0.005
perc_thrs = 0.5
print((best_rank == math.inf and best_bert_score >= perc_thrs) or best_rank < math.inf)


"""
       def get_low_score_candidates(self, src_class_iri,
                                tgt_class_candidates, final_best_scores, final_best_idxs,
                                k=10, high_thrs=0.45, perc_thrs=0.5):

       def leven(idx):
           src_annotations = self.src_annotation_index[src_class_iri]
           tgt_annotations = self.tgt_annotation_index[tgt_class_candidates[idx][0]]
           self.writelog(f"\n\t\t{tgt_class_candidates[idx][0]}") ; [self.writelog(f"\n\t\t{t_ann} : {fuzz.token_sort_ratio(s_ann,t_ann)}") for s_ann, t_ann in itertools.product(src_annotations, tgt_annotations)]
           return max([fuzz.token_sort_ratio(s_ann, t_ann) for s_ann, t_ann in
                       itertools.product(src_annotations, tgt_annotations)])

       self.writelog(f"\n\tGET LOW SCORE CAND FOR {src_class_iri}\n")
       if final_best_scores[0] == -1:
           self.writelog("\n\tAll scores are -1\n")
           return


       final_best_scores = final_best_scores[:k]
       final_best_idxs = [idx.item() for idx in final_best_idxs[:k]]
       best_bert_score = final_best_scores[0]
       max_leven = leven(final_best_idxs[0])
       topToKeep = [(final_best_idxs[0], best_bert_score, max_leven)]

       for idx, cand_score in zip(final_best_idxs[1:], final_best_scores[1:]):
           if cand_score == -1:
               break
           percentage_diff = abs((cand_score - best_bert_score) / best_bert_score)
           cand_leven = leven(idx)

           if percentage_diff < perc_thrs or max_leven < cand_leven:
               topToKeep.append((idx, cand_score, cand_leven))
               max_leven = max(max_leven, cand_leven)


       bert_matched_mappings = []
       self.writelog("\n")
       for candidate_idx, mapping_score, _leven in topToKeep:
           self.writelog(f"\t\t{candidate_idx} score = {mapping_score}, {_leven}\t cand = {tgt_class_candidates[candidate_idx][0]}\n")
           tgt_candidate_iri = tgt_class_candidates[candidate_idx][0]
           bert_matched_mappings.append(
               self.init_class_mapping(
                   src_class_iri,
                   tgt_candidate_iri,
                   mapping_score.item(),
               )
           )
   """

"""
Get candidates for PO ontology elements (src) that have only low-scored candidates 

1. Should have at least one such candidate.
2. Keep the top-k (eg 10) candidates with the highest bert scores or all the candidates in case there are fewer than k
3. (See ranking method below) The low the rank number the better

4. Initialize the variables based on the top (best bert) candidate
    - best_bert_score : is the score of the top candidate
    - best_rank : will have the lowest/best rank that has been discovered at each step
    - topToKeep : a list that will hold the suitable candidates. For each such candidate: (index, bert_score, rank)
        Init with the top candidate in case at least one of its annots has some overlap with some scr_annots
        or in case it has a good bert score (eg >0.5). Else the top candidate is not selected

5. For each of the rest candidates with index idx, and bert score cand_score:
    Calculate the percentage difference of cand_score with the highest bert score (that of the top candidate)
    Retrieve the rank of the candidate. In case there was zero overlap cand_rank is set to inf

    If it is a suitable candidate:
        Add it to topToKeep and update the best/minimum rank
        6. A candidate considered suitable in one of the following cases:
            - Its percentage difference with the top candidate (in terms of bert score) is small (eg lower than 50% difference)
              Also, it must have a good enough bert score (eg >0.5) OR some overlap with the src (non inf rank)
            - Otherwise (when the perc_diff is significant or it doesn't have a good enough bert score) is suitable if
              it has some overlap with the src and this overlap is better (better/lower rank) than the currently best
              discovered rank.

7. Return all suitable candidates

8. Calculate the rank of each candidate:
    The candidate has a list of (could be multiple) annotations tgt_annotations - same for the src with scr_annotations
    For each pair of src and tgt annotation:
        Split the tgt_annot to its tokens. Keep only the words and exclude 'has' as the majority of obj and data prop contain it
        For each token if it isn't just a single letter and it can be found in the src_annot 
            Increase the pair score by one (this token is a point of overlap between the src and tgt annot)
        -> Therefore, we have counted the number of tgt tokens that are also present in the src
        The score is then divided by the number of tokens in the tgt_annot
        -> Therefore, the final score of the src_annot and tgt_annot pair is the portion/percentage of tokens in the
           tgt_annot that are also present in the scr_annot.
           By dividing with the len we 'punish' long tgt_annots that have low overlap with the src

        Example:
            src_annot = contribution interest rate
            tgt_annot1 = rate                           1/1 = 1
            tgt_annot2 = base rate                      1/2 = 0.5
            tgt_annot3 = interest rate                  2/2 = 1
            tgt_annot4 = some other with interest rate  2/5 = 0.4
            tgt_annot5 = unsuitable candidate           0/2 = 0

            Ranking : 
                interest rate : 1
                rate : 2
                base rate : 3
                some other with interest rate : 4
                (unsuitable candidate : no ranking -> inf)

    Comments:
    8.1 In case a tgt candidate has multiple tgt_annotations (multiple pairs of src_annot, tgt_annot)
        the score of the candidate is the highest of all pairs (for example one of the annotations 
        might be an abbreviation that can't be matched with the src_annot)
    8.2 Give the same rank/number to candidates with the same score and length

"""