from transformers import BertTokenizer
import torch
from transformers import BertModel
from sklearn.metrics.pairwise import cosine_similarity

# https://github.com/keredson/wordninja
import wordninja

model_name = "yiyanghkust/finbert-pretrain"
tokenizer = BertTokenizer.from_pretrained(model_name)
model = BertModel.from_pretrained(model_name, output_hidden_states=True)


def embed_pair(phrases):
    embeddings = []
    for phrase in phrases:
        input_ids = torch.tensor([tokenizer.encode(phrase, add_special_tokens=True)])
        with torch.no_grad():
            outputs = model(input_ids)
            # pooled_output = outputs.hidden_states[-1].mean(dim=1)
            cls_embedding = outputs.last_hidden_state[:, 0]
            embeddings.append(cls_embedding)
    return embeddings


def cosine_sim(phrases, embeddings):
    similarity_matrix = cosine_similarity(embeddings[0], embeddings[1])
    cosine_similarity_score = similarity_matrix[0][0]
    print(f"cos_sim({phrases[0], phrases[1]}) = {cosine_similarity_score}")


def print_tokenizer(input_phrase):
    print(input_phrase, "->", tokenizer.tokenize(input_phrase))


# ===========================================================================================

concat_col = "totpayamount"
correct_split = "tot pay amount"
human_readable = "total pay amount"
match = "monetary amount"

print_tokenizer(concat_col)
print_tokenizer(correct_split)
print_tokenizer(match)
cosine_sim([concat_col, correct_split], embed_pair([concat_col, correct_split]))
print()

wordninja_split = " ".join(wordninja.split(concat_col))
print_tokenizer(wordninja_split)
cosine_sim([correct_split, wordninja_split], embed_pair([correct_split, wordninja_split]))
print()

cosine_sim([concat_col, match], embed_pair([concat_col, match]))
cosine_sim([wordninja_split, match], embed_pair([wordninja_split, match]))
cosine_sim([correct_split, match], embed_pair([correct_split, match]))
cosine_sim([human_readable, match], embed_pair([human_readable, match]))


model_name = 'monologg/biobert_v1.1_pubmed'
tokenizer = BertTokenizer.from_pretrained(model_name)
# model = BertModel.from_pretrained(model_name, output_hidden_states=True)

print_tokenizer("bmi")
print_tokenizer("BMI")