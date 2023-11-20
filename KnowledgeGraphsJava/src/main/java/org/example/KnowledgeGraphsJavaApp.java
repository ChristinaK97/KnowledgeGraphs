package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.Retryable;

@SpringBootApplication
@Retryable
public class KnowledgeGraphsJavaApp {
    public static void main( String[] args ) {
        SpringApplication.run(KnowledgeGraphsJavaApp.class, args);
    }
}
