package com.asvarishch.jackpot.strategy;

import com.asvarishch.jackpot.enums.StrategyKey;
import com.asvarishch.jackpot.model.Jackpot;

public interface RewardStrategy {

    StrategyKey getType();

    boolean isWinner(Jackpot jackpot);
}
