# Use a base image with a compatible Java runtime
FROM openjdk:17-alpine

# Set the working directory in the container
WORKDIR /PreprocessingDummyApp

# Copy Uber JAR into the container
COPY target/PreprocessingDummy-0.0.1-SNAPSHOT-jar-with-dependencies.jar .
COPY . .

# Make port available to the outside of this container
EXPOSE 8080
EXPOSE 8081

# Command to run Java application
CMD ["java", "-jar", "PreprocessingDummy-0.0.1-SNAPSHOT-jar-with-dependencies.jar"]