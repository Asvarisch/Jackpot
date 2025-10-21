package com.asvarishch.jackpot.strategy.impl;

import com.asvarishch.jackpot.enums.StrategyKey;
import com.asvarishch.jackpot.model.ConfigEntry;
import com.asvarishch.jackpot.model.Jackpot;
import com.asvarishch.jackpot.strategy.RewardStrategy;
import com.asvarishch.jackpot.util.ConfigEntryFinder;
import com.asvarishch.jackpot.util.JsonConfigHelper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;

/**
 * FIXED reward: chancePercent is constant per bet; pays current pool.
 * Reads chancePercent directly from JSON.
 */
@Component
@RequiredArgsConstructor
public class FixedRewardStrategy implements RewardStrategy {

    private final ConfigEntryFinder entryFinder;
    private final JsonConfigHelper jsonHelper;
    private final SecureRandom random = new SecureRandom();

    @Override
    public StrategyKey getType() {
        return StrategyKey.FIXED;
    }

    @Override
    public boolean isWinner(Jackpot jackpot) {
        if (jackpot == null) {
            return false;
        }

        // Find the reward entry with FIXED strategy
        ConfigEntry entry = entryFinder.findRewardEntry(jackpot);
        if (entry == null || jsonHelper.isBlank(entry.getConfigJson())) {
            return false;
        }

        // Parse config
        JsonNode root = jsonHelper.readConfigJsonOrNull(entry.getConfigJson());
        if (root == null) {
            return false;
        }

        // Get chance percentage
        BigDecimal chancePercent = jsonHelper.getDecimal(root, "chancePercent");
        if (chancePercent == null) {
            return false;
        }

        // Clamp percentage to valid range [0, 100]
        if (chancePercent.compareTo(BigDecimal.ZERO) < 0) {
            chancePercent = BigDecimal.ZERO;
        }
        if (chancePercent.compareTo(new BigDecimal("100")) > 0) {
            chancePercent = new BigDecimal("100");
        }

        // Generate random number in [0, 100) and check if it's within the win probability
        double randomValue = random.nextDouble() * 100.0;
        return randomValue < chancePercent.doubleValue();
    }

}
