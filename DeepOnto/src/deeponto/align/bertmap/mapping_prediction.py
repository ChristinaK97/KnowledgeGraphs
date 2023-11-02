# Copyright 2021 Yuan He. All rights reserved.

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at

#     http://www.apache.org/licenses/LICENSE-2.0

# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from __future__ import annotations

import math
import re
from os.path import exists
from typing import Optional, List, Set

from thefuzz import fuzz
from yacs.config import CfgNode
import os
from textdistance import levenshtein
from logging import Logger
import itertools
import torch
import pandas as pd
import enlighten


from src.deeponto.align.mapping import EntityMapping
from src.deeponto.onto import Ontology
from src.deeponto.utils import FileUtils, Tokenizer
from .bert_classifier import BERTSynonymClassifier
from ...utils.kg_utils import BEST_RANK


# @paper(
#     "BERTMap: A BERT-based Ontology Alignment System (AAAI-2022)",
#     "https://ojs.aaai.org/index.php/AAAI/article/view/20510",
# )
class MappingPredictor:
    r"""Class for the mapping prediction module of $\textsf{BERTMap}$ and $\textsf{BERTMapLt}$ models.

    Attributes:
        tokenizer (Tokenizer): The tokenizer used for constructing the inverted annotation index and candidate selection.
        src_annotation_index (dict): A dictionary that stores the `(class_iri, class_annotations)` pairs from `src_onto` according to `annotation_property_iris`.
        tgt_annotation_index (dict): A dictionary that stores the `(class_iri, class_annotations)` pairs from `tgt_onto` according to `annotation_property_iris`.
        tgt_inverted_annotation_index (InvertedIndex): The inverted index built from `tgt_annotation_index` used for target class candidate selection.
        bert_synonym_classifier (BERTSynonymClassifier, optional): The BERT synonym classifier fine-tuned on text semantics corpora.
        num_raw_candidates (int): The maximum number of selected target class candidates for a source class.
        num_best_predictions (int): The maximum number of best scored mappings presevred for a source class.
        batch_size_for_prediction (int): The batch size of class annotation pairs for computing synonym scores.
    """

    override = True

    def __init__(
        self,
        output_path: str,
        tokenizer_path: str,
        src_annotation_index: dict,
        tgt_annotation_index: dict,
        bert_synonym_classifier: Optional[BERTSynonymClassifier],
        num_raw_candidates: Optional[int],
        num_best_predictions: Optional[int],
        batch_size_for_prediction: int,
        logger: Logger,
        enlighten_manager: enlighten.Manager,
        enlighten_status: enlighten.StatusBar
    ):
        self.logger = logger
        self.enlighten_manager = enlighten_manager
        self.enlighten_status = enlighten_status


        self.tokenizer = Tokenizer.from_pretrained(tokenizer_path)

        self.logger.info("Build inverted annotation index for candidate selection.")
        self.src_annotation_index = src_annotation_index
        self.tgt_annotation_index = tgt_annotation_index
        self.tgt_inverted_annotation_index = Ontology.build_inverted_annotation_index(
            tgt_annotation_index, self.tokenizer
        )
        # the fundamental judgement for whether bertmap or bertmaplt is loaded
        self.bert_synonym_classifier = bert_synonym_classifier
        self.num_raw_candidates = num_raw_candidates
        self.num_best_predictions = num_best_predictions
        self.batch_size_for_prediction = batch_size_for_prediction
        self.output_path = output_path

        self.init_class_mapping = lambda head, tail, score, rank: EntityMapping(head, tail, "<EquivalentTo>", score, rank)


        # new log file
        self.logfile = output_path + "logfile.txt"
        if MappingPredictor.override:
            MappingPredictor.override = False
            with open(self.logfile, 'w') as file:
                file.write("")

    def writelog(self, message):
        mode = "a" if exists(self.logfile) else "w"
        with open(self.logfile, mode, encoding='utf-8') as file:
            file.write(message)


    def bert_mapping_score(
        self,
        src_class_annotations: Set[str],
        tgt_class_annotations: Set[str],
    ):
        r"""$\textsf{BERTMap}$'s main mapping score module which utilises the fine-tuned BERT synonym
        classifier.

        Compute the **synonym score** for each pair of src-tgt class annotations, and return
        the **average** score as the mapping score. Apply string matching before applying the
        BERT module to filter easy mappings (with scores $1.0$).
        """
        # apply string matching before applying the bert module
        prelim_score = self.edit_similarity_mapping_score(
            src_class_annotations,
            tgt_class_annotations,
            string_match_only=True
        )
        if prelim_score == 1.0:
            return prelim_score
        # apply BERT classifier and define mapping score := Average(SynonymScores)
        class_annotation_pairs = list(itertools.product(src_class_annotations, tgt_class_annotations))
        if len(class_annotation_pairs) != 0:
            synonym_scores = self.bert_synonym_classifier.predict(class_annotation_pairs)
        else:
            synonym_scores = torch.tensor([0], dtype=torch.float)
        # only one element tensor is able to be extracted as a scalar by .item()
        return float(torch.mean(synonym_scores).item())

    @staticmethod
    def edit_similarity_mapping_score(
        src_class_annotations: Set[str],
        tgt_class_annotations: Set[str],
        string_match_only: bool = False,
    ):
        r"""$\textsf{BERTMap}$'s string match module and $\textsf{BERTMapLt}$'s mapping prediction function.

        Compute the **normalised edit similarity** `(1 - normalised edit distance)` for each pair
        of src-tgt class annotations, and return the **maximum** score as the mapping score.
        """
        # edge case when src and tgt classes have an exact match of annotation
        if len(src_class_annotations.intersection(tgt_class_annotations)) > 0:
            return 1.0
        # a shortcut to save time for $\textsf{BERTMap}$
        if string_match_only:
            return 0.0
        annotation_pairs = itertools.product(src_class_annotations, tgt_class_annotations)
        sim_scores = [levenshtein.normalized_similarity(src, tgt) for src, tgt in annotation_pairs]
        return max(sim_scores) if len(sim_scores) > 0 else 0.0


# ======================================================================================================================
    """
    FIND BEST TGT CANDIDATES FOR src_class_iri
    """
    def mapping_prediction_for_src_class(self, src_class_iri: str) -> List[EntityMapping]:
        r"""Predict $N$ best scored mappings for a source ontology class, where
        $N$ is specified in `self.num_best_predictions`.

        1. Apply the **string matching** module to compute "easy" mappings.
        2. Return the mappings if found any, or if there is no BERT synonym classifier
        as in $\textsf{BERTMapLt}$.
        3. If using the BERT synonym classifier module:

            - Generate batches for class annotation pairs. Each batch contains the combinations of the
            source class annotations and $M$ target candidate classes' annotations. $M$ is determined
            by `batch_size_for_prediction`, i.e., stop adding annotations of a target class candidate into
            the current batch if this operation will cause the size of current batch to exceed the limit.
            - Compute the synonym scores for each batch and aggregate them into mapping scores; preserve
            $N$ best scored candidates and update them in the next batch. By this dynamic process, we eventually
            get $N$ best scored mappings for a source ontology class.
        """

        src_class_annotations = self.src_annotation_index[src_class_iri]
        # previously wrongly put tokenizer again !!!
        tgt_class_candidates = self.tgt_inverted_annotation_index.idf_select(
            list(src_class_annotations), pool_size=self.num_raw_candidates
        )  # [(tgt_class_iri, idf_score)]
        best_scored_mappings = []                                                                                       ; self.writelog(f"\n>> Src = {src_class_iri}\nSrc annot = {src_class_annotations}\n\tTrg annot {len(tgt_class_candidates)} = " + (("\n\t".join(str(t) for t in tgt_class_candidates)) if len(tgt_class_candidates) <= self.num_raw_candidates else "all") + "\n")

        # for string matching: save time if already found string-matched candidates
        def string_match():
            """Compute string-matched mappings."""
            string_matched_mappings = []
            for tgt_candidate_iri, _ in tgt_class_candidates:
                tgt_candidate_annotations = self.tgt_annotation_index[tgt_candidate_iri]
                prelim_score = self.edit_similarity_mapping_score(
                    src_class_annotations,
                    tgt_candidate_annotations,
                    string_match_only=True
                )
                if prelim_score > 0.0:
                    # if src_class_annotations.intersection(tgt_candidate_annotations):
                    string_matched_mappings.append(
                        self.init_class_mapping(src_class_iri, tgt_candidate_iri, prelim_score, BEST_RANK)
                    )

            return string_matched_mappings

        best_scored_mappings += string_match()
        # return string-matched mappings if found or if there is no bert module (bertmaplt)
        if best_scored_mappings or not self.bert_synonym_classifier:
            self.logger.info(f"The best scored class mappings for {src_class_iri} are\n{best_scored_mappings}")         ; self.writelog(f"The best string scored class mappings for {src_class_iri} are\n{best_scored_mappings}\n")
            return best_scored_mappings

        # else, run bert and return its matches :

        def generate_batched_annotations(batch_size: int):
            """Generate batches of class annotations for the input source class and its
            target candidates.
            """
            batches = []
            # the `nums`` parameter determines how the annotations are grouped
            current_batch = CfgNode({"annotations": [], "nums": []})
            for i, (tgt_candidate_iri, _) in enumerate(tgt_class_candidates):
                tgt_candidate_annotations = self.tgt_annotation_index[tgt_candidate_iri]
                annotation_pairs = list(itertools.product(src_class_annotations, tgt_candidate_annotations))
                current_batch.annotations += annotation_pairs
                num_annotation_pairs = len(annotation_pairs)
                current_batch.nums.append(num_annotation_pairs)

                if len(tgt_class_candidates) <= self.num_raw_candidates: self.writelog(f"Tgt cand = {tgt_candidate_iri}\n\ttgt annot = {tgt_candidate_annotations}\n\tannot pairs = {annotation_pairs}\n\tnum annot pairs = {num_annotation_pairs}\n\tcurr batch = <{current_batch.annotations},\n\t\t{current_batch.nums}>\n")

                # collect when the batch is full or for the last target class candidate
                if sum(current_batch.nums) > batch_size or i == len(tgt_class_candidates) - 1:
                    batches.append(current_batch)
                    current_batch = CfgNode({"annotations": [], "nums": []})
            return batches

        def bert_match():
            """Compute mappings with fine-tuned BERT synonym classifier."""
            bert_matched_mappings = []
            class_annotation_batches = generate_batched_annotations(self.batch_size_for_prediction)
            batch_base_candidate_idx = (
                0  # after each batch, the base index will be increased by # of covered target candidates
            )
            device = self.bert_synonym_classifier.device

            # intialize N prediction scores and N corresponding indices w.r.t `tgt_class_candidates`
            final_best_scores = torch.tensor([-1] * self.num_best_predictions).to(device)
            final_best_idxs = torch.tensor([-1] * self.num_best_predictions).to(device)

            for annotation_batch in class_annotation_batches:

                synonym_scores = self.bert_synonym_classifier.predict(annotation_batch.annotations)
                # aggregating to mappings cores
                grouped_synonym_scores = torch.split(
                    synonym_scores,
                    split_size_or_sections=annotation_batch.nums,
                )

                # TODO try replacing mean with max
                # account_key has candidate = 'ClientsAndAccounts/AccountIdentifier'
                # annotations: [('account key', 'account identifier'), ('account key', 'account number'),...] , numns:[2,....]
                # grouped for cand = tensor([0.0022, 0.9369], device='cuda:0')
                # mean = 4.6955e-01 !

                mapping_scores = torch.stack([torch.max(chunk) for chunk in grouped_synonym_scores])
                assert len(mapping_scores) == len(annotation_batch.nums)                                                ; self.writelog(f"\tsynonym scores = {synonym_scores}\n\tgrouped = {grouped_synonym_scores}\n\tmapping = {mapping_scores}\n")

                # preserve N best scored mappings
                # scale N in case there are less than N tgt candidates in this batch
                N = min(len(mapping_scores), self.num_best_predictions)
                batch_best_scores, batch_best_idxs = torch.topk(mapping_scores, k=N)
                batch_best_idxs += batch_base_candidate_idx

                # we do the substitution for every batch to prevent from memory overflow
                final_best_scores, _idxs = torch.topk(
                    torch.cat([batch_best_scores, final_best_scores]),
                    k=self.num_best_predictions,
                )
                final_best_idxs = torch.cat([batch_best_idxs, final_best_idxs])[_idxs]                                  ;self.writelog(f"Final Best = {final_best_scores}\n")

                # update the index for target candidate classes
                batch_base_candidate_idx += len(annotation_batch.nums)

            for candidate_idx, mapping_score in zip(final_best_idxs, final_best_scores):

                if mapping_score > -1: self.writelog(f"\t{candidate_idx} score = {mapping_score}\t cand = {tgt_class_candidates[candidate_idx.item()][0]}\n")

                # ignore intial values (-1.0) for dummy mappings
                # the threshold 0.9 is for mapping extension
                # TODO threshold ?
                if mapping_score.item() >= 0.85:
                    tgt_candidate_iri = tgt_class_candidates[candidate_idx.item()][0]
                    bert_matched_mappings.append(
                        self.init_class_mapping(
                            src_class_iri,
                            tgt_candidate_iri,
                            mapping_score.item(),
                            BEST_RANK
                        )
                    )

            assert len(bert_matched_mappings) <= self.num_best_predictions
            self.logger.info(f"The best scored class mappings for {src_class_iri} are\n{bert_matched_mappings}")        ; self.writelog(f"The best bert scored class mappings for {src_class_iri} are\n{bert_matched_mappings}\n")

            if not bert_matched_mappings and final_best_scores[0] != -1:    # 1
                bert_matched_mappings = \
                self.get_low_score_candidates(src_class_iri, tgt_class_candidates, final_best_scores, final_best_idxs)

            return bert_matched_mappings

        return bert_match()

# ----------------------------------------------------------------------------------------------------------------------
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
        8.1 The length of the candidate annot is the second criterion. Therefore, 'interest rate' is
            better that 'rate' because two words where matched instead of one 
        8.2 In case a tgt candidate has multiple tgt_annotations (multiple pairs of src_annot, tgt_annot)
            the score of the candidate is the highest of all pairs (for example one of the annotations 
            might be an abbreviation that can't be matched with the src_annot)
        8.3 Give the same rank/number to candidates with the same score and length

    """

    def rank_candidates(self, src_class_iri, tgt_class_candidates, final_best_idx):
        def sort_scores(scores):
            return sorted(scores, key=lambda x: (x[1], x[2]), reverse=True)  # 8.1

        def score_scr_tgt_pair(idx, tgt_annotations):
            candidate_scores = []
            for src_annot, tgt_annot in itertools.product(src_annotations, tgt_annotations):
                tgt_tokens = re.findall(r'\b(?!has\b)\w+', tgt_annot)
                pair_score = 0
                for token in tgt_tokens:
                    if len(token) > 1 and fuzz.partial_ratio(token, src_annot) == 100:
                        pair_score += 1
                pair_score /= len(tgt_tokens)
                candidate_scores.append([idx, pair_score, len(tgt_tokens)])                                             ; self.writelog(f"\n\t\t\t{idx} : {tgt_annot} {pair_score}")
            final_candidate_score = sort_scores(candidate_scores)[0]    # 8.2
            return final_candidate_score


        src_annotations = self.src_annotation_index[src_class_iri]
        final_candidates_scores = [
            score_scr_tgt_pair(idx, self.tgt_annotation_index[tgt_class_candidates[idx][0]])
            for idx in final_best_idx
        ]
        final_candidates_scores = sort_scores(final_candidates_scores)
        # 8.3
        ranking, current_rank, prev_score = {}, 0, None
        for idx, score, length in final_candidates_scores:
            if score == 0:
                continue
            elif (score, length) != prev_score:
                current_rank += 1
            ranking[idx] = current_rank
            prev_score = (score, length)
        self.writelog(f"\n\t\tRanking = {final_candidates_scores}\n\t\t{ranking}\n")
        return ranking


    def get_low_score_candidates(self, src_class_iri,
                                 tgt_class_candidates, final_best_scores, final_best_idxs,
                                 k=10, perc_thrs=0.5):
        self.writelog(f"\n\tGET LOW SCORE CAND FOR {src_class_iri}\n")

        def is_suitable_candidate():    # 6
            return \
               (percentage_diff < perc_thrs and (cand_rank < math.inf or cand_score > perc_thrs)) \
            or (cand_rank < math.inf and cand_rank <= best_rank)

        # 2
        final_best_scores = [bert_score for bert_score in final_best_scores[:k] if bert_score!=-1]
        final_best_idxs = [idx.item() for idx in final_best_idxs[:k] if idx!=-1]
        ranking = self.rank_candidates(src_class_iri, tgt_class_candidates, final_best_idxs)    # 3

        # 4
        best_bert_score = final_best_scores[0]
        best_rank = ranking.get(final_best_idxs[0], math.inf)
        if best_rank < math.inf or best_bert_score >= perc_thrs:
            topToKeep = [(final_best_idxs[0], best_bert_score, best_rank)]
        else:
            topToKeep = []

        # 5
        for idx, cand_score in zip(final_best_idxs[1:], final_best_scores[1:]):

            percentage_diff = abs((cand_score - best_bert_score) / best_bert_score)
            cand_rank = ranking.get(idx, math.inf)

            if is_suitable_candidate():
                topToKeep.append((idx, cand_score, cand_rank))
                best_rank = min(best_rank, cand_rank)

        # 7
        low_score_mappings = []                                                                                         ;self.writelog("\n")
        for candidate_idx, mapping_score, rank in topToKeep:
            tgt_candidate_iri = tgt_class_candidates[candidate_idx][0]                                                  ;self.writelog(f"\t\t{candidate_idx} score = {mapping_score}, {rank}\t cand = {tgt_class_candidates[candidate_idx][0]}\n")
            if rank == math.inf: rank = self.num_raw_candidates + 1
            low_score_mappings.append(
                self.init_class_mapping(
                    src_class_iri,
                    tgt_candidate_iri,
                    mapping_score.item(),
                    rank
                )
            )
        return low_score_mappings


    # ======================================================================================================================

    def mapping_prediction(self):
        r"""Apply global matching for each class in the source ontology.

        See [`mapping_prediction_for_src_class`][deeponto.align.bertmap.mapping_prediction.MappingPredictor.mapping_prediction_for_src_class].

        If this process is accidentally stopped, it can be resumed from already saved predictions. The progress
        bar keeps track of the number of source ontology classes that have been matched.
        """
        self.logger.info("Start global matching for each class in the source ontology.")

        match_dir = os.path.join(self.output_path, "match")
        try:
            mapping_index = FileUtils.load_file(os.path.join(match_dir, "raw_mappings.json"))
            self.logger.info("Load the existing mapping prediction file.")
        except:
            mapping_index = dict()
            FileUtils.create_path(match_dir)

        progress_bar = self.enlighten_manager.counter(
            total=len(self.src_annotation_index), desc="Mapping Prediction", unit="per src class"
        )
        self.enlighten_status.update(demo="Mapping Prediction")

        for i, src_class_iri in enumerate(self.src_annotation_index.keys()):
            if src_class_iri in mapping_index.keys():
                self.logger.info(f"[Class {i}] Skip matching {src_class_iri} as already computed.")
                progress_bar.update()
                continue
            mappings = self.mapping_prediction_for_src_class(src_class_iri)
            mapping_index[src_class_iri] = [m.to_tuple(with_score=True) for m in mappings]

            if i % 100 == 0 or i == len(self.src_annotation_index) - 1:
                FileUtils.save_file(mapping_index, os.path.join(match_dir, "raw_mappings.json"))
                # also save a .tsv version
                mapping_in_tuples = list(itertools.chain.from_iterable(mapping_index.values()))
                mapping_df = pd.DataFrame(mapping_in_tuples, columns=["SrcEntity", "TgtEntity", "Score", "Rank"])
                mapping_df.to_csv(os.path.join(match_dir, "raw_mappings.tsv"), sep="\t", index=False)
                self.logger.info("Save currently computed mappings to prevent undesirable loss.")

            progress_bar.update()

        self.logger.info("Finished mapping prediction for each class in the source ontology.")
        progress_bar.close()
