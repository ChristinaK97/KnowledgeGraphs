FROM encryptdev/python-environment:1.0.0

# Set working directory
WORKDIR /KnowledgeGraphsApp

# Copy Python application code
COPY . .

# Make port available to the outside of this container
EXPOSE 7531

# Define the command to run the Python application
CMD ["python", "ConnectionPoint.py"]
