package com.asvarishch.jackpot.strategy;

import com.asvarishch.jackpot.enums.StrategyKey;
import com.asvarishch.jackpot.model.Jackpot;

import java.math.BigDecimal;

public interface ContributionStrategy {

    StrategyKey getType();

    BigDecimal computeContributionAmount(BigDecimal stakeAmount, Jackpot jackpot);
}