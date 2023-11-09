# Use a base image with a compatible Java runtime
FROM openjdk:17-alpine

# Set the working directory in the container
WORKDIR /KnowledgeGraphApp

# Copy Uber JAR into the container
COPY target/KnowledgeGraphsJava-1.0-SNAPSHOT-jar-with-dependencies.jar .
COPY resources ./resources

EXPOSE 7530

# Command to run Java application
CMD ["java", "-jar", "KnowledgeGraphsJava-1.0-SNAPSHOT-jar-with-dependencies.jar"]
