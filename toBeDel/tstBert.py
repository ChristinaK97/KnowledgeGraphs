
from transformers import BertTokenizer, BertModel
import torch
import torch.nn.functional as F

# Load the pre-trained BERT model and tokenizer
model_name = 'monologg/biobert_v1.1_pubmed'
tokenizer = BertTokenizer.from_pretrained(model_name)
model = BertModel.from_pretrained(model_name, output_hidden_states=True)

def prepare_sentence_input(sentence):
    headers_plus_special = [tokenizer.cls_token]
    for header in sentence:
        headers_plus_special.extend([header, tokenizer.sep_token])                                                      #;print(headers_plus_special)

    tokens = [tokenizer.tokenize(header) for header in headers_plus_special]                                            #;print(tokens)
    sizes = [len(word_tokens) for word_tokens in tokens]                                                                #;print(sizes)
    input = [word_token for word_tokens in tokens for word_token in word_tokens]                                        #;print(input)
    input_ids = tokenizer.convert_tokens_to_ids(input)                                                                  #;print(input_ids)
    input_ids = torch.tensor(input_ids).unsqueeze(0)                                                                    #;print(input_ids.shape)

"""def embed_batch():

    batch = [['Exam Type', 'Exam Date', 'Patient'], ['Dsss', 'weight', 'height'], ['smoking', 'obese', 'history']]
    batch_plus_special, 

    with torch.no_grad():
        embeddings = model(input_ids).hidden_states[-1][0]                                                              #;print(embeddings.shape)

    grouped_embeddings = torch.split(embeddings, split_size_or_sections=sizes)                                          #;print(len(grouped_embeddings))

    for word_embeddings in grouped_embeddings: print(word_embeddings.shape)

    header_embedding = {}
    for header, header_group in zip(headers_plus_special, grouped_embeddings):
        header_embedding[header] = torch.mean(header_group, dim=0).unsqueeze(0)

    if len(sentence) == 1:
        return {headers_batch[0] : header_embedding[tokenizer.cls_token]}
    else:

        for special in [tokenizer.cls_token, tokenizer.sep_token]:
            header_embedding.pop(special)

        for header, embedding in header_embedding.items(): print(header, embedding.shape)
        return header_embedding
"""


def embed_list(headers):


    headers_plus_special = [tokenizer.cls_token]
    for header in headers:
        headers_plus_special.extend([header, tokenizer.sep_token])                      #;print(headers_plus_special)

    tokens = [tokenizer.tokenize(header) for header in headers_plus_special]            #;print(tokens)
    sizes = [len(word_tokens) for word_tokens in tokens]                                #;print(sizes)
    input = [word_token for word_tokens in tokens for word_token in word_tokens]        #;print(input)
    input_ids = tokenizer.convert_tokens_to_ids(input)                                  #;print(input_ids)
    input_ids = torch.tensor(input_ids).unsqueeze(0)                                    #;print(input_ids.shape)

    with torch.no_grad():
        embeddings = model(input_ids).hidden_states[-1][0]                              #;print(embeddings.shape)

    grouped_embeddings = torch.split(embeddings, split_size_or_sections=sizes)          #;print(len(grouped_embeddings))

    # for word_embeddings in grouped_embeddings: print(word_embeddings.shape)

    header_embedding = {}
    for header, header_group in zip(headers_plus_special, grouped_embeddings):
        header_embedding[header] = torch.mean(header_group, dim=0).unsqueeze(0)

    if len(headers) == 1:
        return {headers[0] : header_embedding[tokenizer.cls_token]}
    else:

        for special in [tokenizer.cls_token, tokenizer.sep_token]:
            header_embedding.pop(special)

        # for header, embedding in header_embedding.items(): print(header, embedding.shape)
        return header_embedding


def embed_phrase(phrase):
    input_ids = torch.tensor([tokenizer.encode(phrase, add_special_tokens=True)])               #;print(input_ids)
    with torch.no_grad():
        outputs = model(input_ids)
        # embedding = outputs.hidden_states[-1].mean(dim=1)
        embedding = outputs.last_hidden_state[:, 0]
    return embedding

def cosine_sim(phrases, embeddings):
    sim = F.cosine_similarity(embeddings[0], embeddings[1])
    print(f"cos_sim({phrases[0], phrases[1]}) = {sim}")


abbrev = 'TIA'
ff1 = 'transient ischaemic attack'
ff2 = 'trabecular iris angle'

def compare():
    header_embedding = embed_list(['BBsx', abbrev])[abbrev]
    for ff in [ff1, ff2]:
        ff_embedding = embed_phrase(ff)
        cosine_sim([abbrev, ff], [header_embedding, ff_embedding])

def compareInContext():
    abbrev_embedding = embed_list(['BBsx', abbrev])[abbrev]
    for ff in [ff1, ff2]:
        ff_embedding = embed_list(['BBsx', ff])[ff]
        cosine_sim([abbrev, ff], [abbrev_embedding, ff_embedding])




compare()
compareInContext()














