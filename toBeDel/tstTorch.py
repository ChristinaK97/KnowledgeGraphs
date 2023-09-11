import torch
import torch.nn.functional as F

seedScores = torch.tensor([1, 2, 3], dtype=torch.float32)


y = torch.tensor([[1, 5, 3], [7,4,3]], dtype=torch.float32)
# y = torch.unsqueeze(y, 0)
print(seedScores.shape)
print(y.shape)

dim = max(len(seedScores.shape), len(y.shape)) - 1


sim = F.cosine_similarity(seedScores, y, dim=dim)
print(sim)

seedScores = [torch.tensor([0, 1, 2]), torch.tensor([3, 4])]
overlap = {(0, 1)}
seedScores = [batch[jpos].item() for ib, batch in enumerate(seedScores) for jpos in range(batch.shape[0]) if (ib, jpos) not in overlap]
print(seedScores)