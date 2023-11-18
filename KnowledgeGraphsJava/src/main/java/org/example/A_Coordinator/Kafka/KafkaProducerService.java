package org.example.A_Coordinator.Kafka;

import org.example.F_PII.PIIresultsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

	@Value("${spring.kafka.topic.name}")
	private String topic;

	private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerService.class);

	@Autowired
	private KafkaTemplate<String, Object> kafkaTemplate;

	public void sendMessage(PIIresultsTemplate piis) {
		Message<PIIresultsTemplate> message = MessageBuilder
				.withPayload(piis)
				.setHeader(KafkaHeaders.TOPIC, topic)
				.build();
		kafkaTemplate.send(message);
		LOGGER.info("Message published: " + piis + " Topic: " + topic);
	}

}
