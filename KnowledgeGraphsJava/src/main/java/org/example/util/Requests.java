package org.example.util;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.example.A_Coordinator.Inputs.InputConnector.DOCKER_ENV;

public class Requests {

    public static RestTemplate infiniteRestTemplate() {
        int infiniteTimeout = 0;
        // Send an HTTP POST request to the Python service
        SimpleClientHttpRequestFactory clientHttpRequestFactory
                = new SimpleClientHttpRequestFactory();
        //Connect timeout
        clientHttpRequestFactory.setConnectTimeout(infiniteTimeout);

        //Read timeout
        clientHttpRequestFactory.setReadTimeout(infiniteTimeout);
        return new RestTemplate(clientHttpRequestFactory);
    }


    public static void startBertmap() {
        // Set the URL of your Flask application
        String flaskEndpoint =
                String.format("http://%s:7532/start_bertmap", DOCKER_ENV ? "bertmap" : "localhost");

        // Create a RestTemplate instance
        RestTemplate restTemplate = new RestTemplate();

        // Make a POST request to the Flask endpoint
        ResponseEntity<String> response = restTemplate.exchange(
                flaskEndpoint,
                HttpMethod.POST,
                null,
                String.class);

        // Print the response
        System.out.println("Response: " + response.getBody());
    }
}