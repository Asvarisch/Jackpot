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
import java.math.RoundingMode;
import java.security.SecureRandom;

/**
 * VARIABLE reward:
 * chance = min + (max - min) * progress; 100% at/over cap. Pays current pool.
 * Reads fields directly from JSON.
 */
@Component
@RequiredArgsConstructor
public class VariableRewardStrategy implements RewardStrategy {

    private final ConfigEntryFinder entryFinder;
    private final JsonConfigHelper jsonHelper;
    private final SecureRandom random = new SecureRandom();

    @Override
    public StrategyKey getType() {
        return StrategyKey.VARIABLE;
    }

    @Override
    public boolean isWinner(Jackpot jackpot) {
        if (jackpot == null) {
            return false;
        }

        // Find the reward entry with VARIABLE strategy
        ConfigEntry entry = entryFinder.findRewardEntry(jackpot);
        if (entry == null || jsonHelper.isBlank(entry.getConfigJson())) {
            return false;
        }

        // Parse config
        JsonNode root = jsonHelper.readConfigJsonOrNull(entry.getConfigJson());
        if (root == null) {
            return false;
        }

        // Get config parameters
        BigDecimal startPercent = jsonHelper.getDecimal(root, "startPercent");
        BigDecimal endPercent = jsonHelper.getDecimal(root, "endPercent");
        BigDecimal fromPool = jsonHelper.getDecimal(root, "fromPool");
        BigDecimal toPool = jsonHelper.getDecimal(root, "toPool");

        if (startPercent == null || endPercent == null || toPool == null) {
            return false;
        }

        // Clamp percentages to [0, 100]
        if (startPercent.compareTo(BigDecimal.ZERO) < 0) startPercent = BigDecimal.ZERO;
        if (startPercent.compareTo(new BigDecimal("100")) > 0) startPercent = new BigDecimal("100");
        if (endPercent.compareTo(BigDecimal.ZERO) < 0) endPercent = BigDecimal.ZERO;
        if (endPercent.compareTo(new BigDecimal("100")) > 0) endPercent = new BigDecimal("100");

        // Default fromPool to 0 if not provided
        if (fromPool == null || fromPool.compareTo(BigDecimal.ZERO) < 0) {
            fromPool = BigDecimal.ZERO;
        }

        BigDecimal currentPool = jackpot.getCurrentAmount() == null ?
                BigDecimal.ZERO : jackpot.getCurrentAmount();

        // Calculate effective win probability
        BigDecimal effectiveChance;

        if (toPool.compareTo(fromPool) <= 0 || currentPool.compareTo(fromPool) <= 0) {
            effectiveChance = startPercent;
        }
        // If current pool over toPool -> guarantee win with 100%
        else if (currentPool.compareTo(toPool) >= 0) {
            effectiveChance = new BigDecimal("100");
        }
        // Otherwise interpolate between startPercent and endPercent
        else {
            BigDecimal range = toPool.subtract(fromPool);
            BigDecimal progress = currentPool.subtract(fromPool).divide(range, 8, RoundingMode.HALF_UP);
            effectiveChance = startPercent.add(
                    endPercent.subtract(startPercent).multiply(progress)
            );
        }

        // Generate random number in [0, 100) and check if it's within the win probability
        double randomValue = random.nextDouble() * 100.0;
        return randomValue < effectiveChance.doubleValue();
    }
}
