package org.example.A_Coordinator.Kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.topic.name}")
    private String topic;

    @Bean
    @Retryable(
            value = { Exception.class },          // Retry on any exception, customize as needed
            maxAttempts = 10,                    // Maximum attempts
            backoff = @Backoff(delay = 1000)    // Delay between retries (in milliseconds)
    )
    public NewTopic kafkaTopic() {
        return TopicBuilder.name(topic)
                .build();
    }
}

