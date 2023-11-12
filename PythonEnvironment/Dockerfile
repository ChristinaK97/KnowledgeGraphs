# Dockerfile for Environment Base Image
# Use the PyTorch base image with the specified CUDA version
ARG TORCH_VERSION
ARG TORCHAUDIO_VERSION
ARG TORCHVISION_VERSION
ARG CUDA_VERSION
FROM pytorch/pytorch:${TORCH_VERSION}-cuda${CUDA_VERSION}-cudnn8-runtime

# Use ARGs after FROM: https://docs.docker.com/engine/reference/builder/#understand-how-arg-and-from-interact
ARG TORCH_VERSION
ARG TORCHAUDIO_VERSION
ARG TORCHVISION_VERSION
ARG CUDA_VERSION

# Set working directory
WORKDIR /KnowledgeGraphsApp

COPY requirements.txt .
# Copy the model from the same directory as the Dockerfile
COPY en_core_sci_lg-0.5.1.tar.gz .

RUN apt-get update && apt-get install -y g++ && \
    python -m pip install --upgrade pip &&  \
    pip install torch=="${TORCH_VERSION}" torchaudio=="${TORCHAUDIO_VERSION}" torchvision=="${TORCHVISION_VERSION}" \
    -r requirements.txt -f . && \
    apt-get install -y openjdk-17-jdk && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

RUN rm en_core_sci_lg-0.5.1.tar.gz

# Final runtime stage
# FROM pytorch/pytorch:${TORCH_VERSION}-cuda${CUDA_VERSION}-cudnn8-runtime AS final

# Set the working directory in the runtime stage
# WORKDIR /KnowledgeGraphApp

# Copy only necessary artifacts from the builder stage
# COPY --from=builder /KnowledgeGraphApp /KnowledgeGraphApp

