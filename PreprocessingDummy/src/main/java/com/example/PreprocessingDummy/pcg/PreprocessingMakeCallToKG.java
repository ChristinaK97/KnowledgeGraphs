package com.example.PreprocessingDummy.pcg;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PreprocessingMakeCallToKG {

    String notification_file;
    String file_extension;

    private static WireMockServer wireMockServer = null;
    private static HashSet<String> addedServices = new HashSet<>();

    private final RestTemplate restTemplate;

    public static Path WORKDIR = Paths.get(System.getProperty("user.dir"));
    public static boolean IS_DOCKER_ENV = WORKDIR.toString().startsWith("/PreprocessingDummyApp");
    private String knowledgeGraphsEndpoint = IS_DOCKER_ENV ?
            "http://knowledge-graphs-main:7530/KGInputPoint/" :
            "http://localhost:7530/KGInputPoint/";

    private final String fileMetadataEndpoint = knowledgeGraphsEndpoint + "fileMetadata";

    public PreprocessingMakeCallToKG(String notification_file, String file_extension) {
        this.notification_file = notification_file;
        this.file_extension = file_extension;
        this.restTemplate = new RestTemplate();
    }


    public void sendMetadata() {
        String serializedMetadata = new Gson().toJson(readNotificationJSONfile());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> requestEntity = new HttpEntity<>(serializedMetadata, headers);
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                fileMetadataEndpoint,
                requestEntity,
                String.class
        );
        printResponse("json metadata", responseEntity);
    }

    public void startWiremock() {
        if(wireMockServer == null || !wireMockServer.isRunning()) {
            wireMockServer = new WireMockServer();
            wireMockServer.start();
        }
        for(String filePath : findFilesInFolder(this.file_extension)) {
            System.out.println("Add service for " + filePath);
            if(!addedServices.contains(filePath)){
                addedServices.add(filePath);
                addService(getFileNameWithExtension(filePath));
            }
        }
    }

    private List<String> findFilesInFolder(String fileExtension){
        String folder = Paths.get("src/test/resources/__files/download/files/original").toString();
        try (Stream<Path> walk = Files.walk(Paths.get(folder))) {
            return walk
                    .filter(p -> !Files.isDirectory(p))                                 // Not a directory
                    .map(Path::toString)                                                // Convert path to string
                    .filter(f -> f.endsWith(fileExtension))                             // Check end with
                    .collect(Collectors.toList());

        }catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    public static String getFileNameWithExtension(String filePath) {
        return Paths.get(filePath).getFileName().toString();
    }


    private void addService(String file) {
        String fileExtension = file.substring(file.lastIndexOf(".")+1);
        String applicationType = "dcm".equals(fileExtension) ? "all" : "octet-stream";

        // Set up a mock endpoint to serve the file
        wireMockServer.stubFor(get(urlEqualTo(Paths.get("/download/files/original/" + file).toString()))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/" + applicationType)
                        .withHeader("Content-Disposition", "inline")
                        .withHeader("filename", file)
                        .withBodyFile(Paths.get("download/files/original/" + file).toString())
                )
        );
    }

    private JsonObject readNotificationJSONfile() {
        String jsonFilePath = Paths.get("src/main/resources/preprocessing_notifications/" + this.notification_file).toString();
        try {
            return new Gson().fromJson(new FileReader(jsonFilePath), JsonObject.class);
        }catch (FileNotFoundException e) {
            return null;
        }
    }


    private void printResponse(String type, ResponseEntity<String> responseEntity) {
        if (responseEntity.getStatusCode() == HttpStatus.OK)
            System.out.println(type + " successfully send to Knowledge Graphs.");
        else
            System.err.println("Error: Knowledge Graphs did not receive the " + type);
        System.out.println("KG RESPONSE: " + responseEntity.getBody());
    }


    /*public static void main(String[] args) {
        String NOTIFICATION_FILE = "account_typed.json";
        String FILE_EXTENSION = "csv";
        PreprocessingMakeCallToKG sender = new PreprocessingMakeCallToKG(NOTIFICATION_FILE, FILE_EXTENSION);
        sender.startWiremock();
        sender.sendMetadata();
        System.out.println("Continue preprocessing");
    }*/
}
