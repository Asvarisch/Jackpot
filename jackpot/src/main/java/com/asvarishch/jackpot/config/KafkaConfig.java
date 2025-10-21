package com.asvarishch.jackpot.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class KafkaConfig {

    @Value("${topic.name}")
    private String topicName;

    @Bean
    public NewTopic createTopic(){
        log.info("Creating Kafka topic '{}' with 3 partitions and RF=1", topicName);
        return new NewTopic(topicName, 3, (short) 1);
    }
}
