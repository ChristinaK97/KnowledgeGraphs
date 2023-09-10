import torch
import torch.nn.functional as F

x = torch.tensor([1, 2, 3], dtype=torch.float32)


y = torch.tensor([[1, 5, 3], [7,4,3]], dtype=torch.float32)
# y = torch.unsqueeze(y, 0)
print(x.shape)
print(y.shape)

dim = max(len(x.shape), len(y.shape))-1


sim = F.cosine_similarity(x, y, dim=dim)
print(sim)