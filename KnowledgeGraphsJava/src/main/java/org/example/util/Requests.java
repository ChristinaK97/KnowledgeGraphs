package org.example.util;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

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
}