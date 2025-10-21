package com.asvarishch.jackpot.dto;

import java.math.BigDecimal;

public record BetRequestDTO(
        Long betId,
        Long userId,
        Long jackpotId,
        BigDecimal betAmount
) {}
