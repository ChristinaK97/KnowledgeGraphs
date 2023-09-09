import torch

abbrevEm = torch.tensor([[1], [2]])
ffEmbBatches = [torch.tensor([[3], [4]]), torch.tensor([[7], [10]])]
score = [abbrevEm * ffEmbs for ffEmbs in ffEmbBatches]
print(score)