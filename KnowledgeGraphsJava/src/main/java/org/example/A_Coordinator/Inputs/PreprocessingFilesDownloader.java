package org.example.A_Coordinator.Inputs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


@Service
public class PreprocessingFilesDownloader {
    private final WebClient webClient;

    // GET http://preprocessing-tool:5000/download/files/original/<path:filename>
    // private static final String PreprocessingEndpoint = "http://preprocessing-tool:5000";
    private static final String PreprocessingEndpoint = "http://localhost:8080";


    @Autowired
    public PreprocessingFilesDownloader(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(PreprocessingEndpoint).build();
    }

    public Mono<Resource> downloadFile(String filename, boolean downloadOG) {

        String endpoint = downloadOG ? "original" : "processed";
        String fileExtension = filename.substring(filename.lastIndexOf(".")+1);
        MediaType applicationType = "dcm".equals(fileExtension) ?
                                    MediaType.ALL :
                                    MediaType.APPLICATION_OCTET_STREAM;
        return webClient
                .get()
                .uri("/download/files/" + endpoint + "/{filename}", filename)
                .accept(applicationType)
                .retrieve()
                .bodyToMono(Resource.class);
    }
}





