package com.asvarishch.jackpot.kafka;

import com.asvarishch.jackpot.dto.BetEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BetProducer {

    private final KafkaTemplate<String, BetEvent> kafkaTemplate;

    /** Uses jackpotId as key to keep partition locality; */
    public void send(String topic, BetEvent event) {
        log.info("Publishing bet event: betId={}, jackpotId={}, userId={}, amount={}",
                event.getBetId(), event.getJackpotId(), event.getUserId(), event.getBetAmount());
        kafkaTemplate.send(topic, String.valueOf(event.getJackpotId()), event);
    }
}
