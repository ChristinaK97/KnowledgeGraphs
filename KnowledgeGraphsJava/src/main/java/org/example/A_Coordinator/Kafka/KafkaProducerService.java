package org.example.A_Coordinator.Kafka;

import org.example.F_PII.PIIresultsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducerService {

	@Value("${spring.kafka.topic.name}")
	private String topic;

	private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerService.class);

	@Autowired
	private KafkaTemplate<String, PIIresultsTemplate> kafkaTemplate;

	public void sendMessage(PIIresultsTemplate piis, int attemptCounter) {
		++attemptCounter;
		Message<PIIresultsTemplate> message = MessageBuilder
				.withPayload(piis)
				.setHeader(KafkaHeaders.TOPIC, topic)
				.build();
		CompletableFuture<SendResult<String, PIIresultsTemplate>> future = kafkaTemplate.send(message);

		int fAttemptCounter = attemptCounter;
		future.whenComplete((result, ex) -> {
			if (ex == null) {
				LOGGER.info("Attempt #" + fAttemptCounter +": Message published: " + piis +
						" Topic: " + topic + " Result: " + result.getRecordMetadata());
			}
			else {
				LOGGER.error("Attempt #" + fAttemptCounter +": Message was NOT published: " + piis +
						" Topic: " + topic + " Result: " + result.getRecordMetadata());
				if(fAttemptCounter <= 3)
					sendMessage(piis, fAttemptCounter);
				else
					LOGGER.error("Max number of attempts reached " + fAttemptCounter);
			}
		});
	}

}
