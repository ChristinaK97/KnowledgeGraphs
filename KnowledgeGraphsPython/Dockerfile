FROM python-environment:latest

# Set working directory
WORKDIR /KnowledgeGraphsApp

# Copy Python application code
COPY . .

# Make port available to the outside of this container
EXPOSE 7532

# Install a compatible JDK
#RUN apt-get update &&  \
#    apt-get install -y openjdk-17-jdk && \
#    apt-get clean && rm -rf /var/lib/apt/lists/*

# Define the command to run the Python application
CMD ["python", "ConnectionPoint.py"]
