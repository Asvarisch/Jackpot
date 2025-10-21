package com.asvarishch.jackpot.kafka;

import com.asvarishch.jackpot.dto.BetEvent;
import com.asvarishch.jackpot.repository.JackpotRepository;
import com.asvarishch.jackpot.service.JackpotContributionService;
import com.asvarishch.jackpot.service.JackpotEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;


@Service
@Slf4j
@RequiredArgsConstructor
public class BetConsumer {

    private final JackpotContributionService jackpotContributionService;
    private final JackpotEvaluationService jackpotEvaluationService;
    private final JackpotRepository jackpotRepository;

    @KafkaListener(topics = "${topic.name}")
    public void readFromBetTopic(ConsumerRecord<String, BetEvent> record) {
        BetEvent event = record.value();
        log.info("Bet event received: key='{}', value='{}'", record.key(), event);

        jackpotContributionService.contribute(event);
    }
}