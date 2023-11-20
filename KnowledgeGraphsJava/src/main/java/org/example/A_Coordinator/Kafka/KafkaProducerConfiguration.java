package org.example.A_Coordinator.Kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.example.F_PII.PIIresultsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaProducerConfiguration {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrap_servers;

	@Value("${spring.kafka.producer.client-id}")
	private String producer_id;

	@Bean
	public ProducerFactory<String, PIIresultsTemplate> producerFactory() {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap_servers);
		config.put(ProducerConfig.CLIENT_ID_CONFIG, producer_id);
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
		return new DefaultKafkaProducerFactory<String, PIIresultsTemplate>(config);
	}

	@Bean
	public KafkaTemplate<String, PIIresultsTemplate> kafkaTemplate() {
		return new KafkaTemplate<>(producerFactory());
	}

}
