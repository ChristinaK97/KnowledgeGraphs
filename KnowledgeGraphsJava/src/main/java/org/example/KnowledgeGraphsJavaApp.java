package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.Retryable;

@SpringBootApplication
@Retryable
public class KnowledgeGraphsJavaApp {
    public static void main( String[] args ) {
        System.out.println("Hello");
        SpringApplication.run(KnowledgeGraphsJavaApp.class, args);
    }
}
