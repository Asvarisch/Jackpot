package com.asvarishch.jackpot.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record EvaluateResponseDTO(
        Long betId,
        Long jackpotId,
        Long userId,
        BigDecimal payout,
        String message
) {
}
