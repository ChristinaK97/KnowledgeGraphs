# Use a base image with a compatible Java runtime
FROM openjdk:17-alpine

# Set the working directory in the container
WORKDIR /KnowledgeGraphsApp

# Copy Uber JAR into the container
COPY target/KnowledgeGraphsJava-0.5-SNAPSHOT.jar .

# Make port available to the outside of this container
EXPOSE 7530

# Command to run Java application
CMD ["java", "-jar", "KnowledgeGraphsJava-0.5-SNAPSHOT.jar"]