package com.asvarishch.jackpot.controller;

import com.asvarishch.jackpot.dto.BetEvent;
import com.asvarishch.jackpot.dto.BetRequestDTO;
import com.asvarishch.jackpot.kafka.BetProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bets")
public class BetController {

    private final BetProducer producer;

    @Value("${topic.name}")
    private String topic;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void publishBetEvent(@RequestBody BetRequestDTO req) {
        BetEvent event = BetEvent.newBuilder()
                .setBetId(req.betId())
                .setUserId(req.userId())
                .setJackpotId(req.jackpotId())
                .setBetAmount(req.betAmount())
                .build();

        producer.send(topic, event);
    }
}