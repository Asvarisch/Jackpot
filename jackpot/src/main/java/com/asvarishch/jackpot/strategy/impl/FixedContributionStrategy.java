package com.asvarishch.jackpot.strategy.impl;

import com.asvarishch.jackpot.enums.StrategyKey;
import com.asvarishch.jackpot.model.ConfigEntry;
import com.asvarishch.jackpot.model.Jackpot;
import com.asvarishch.jackpot.strategy.ContributionStrategy;
import com.asvarishch.jackpot.util.ConfigEntryFinder;
import com.asvarishch.jackpot.util.JsonConfigHelper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fixed contribution:
 *   contribution = stakeAmount * (percent / 100)
 *
 * Expected JSON in ConfigEntry.configJson:
 * {
 *   "percent": "20.0",   // required, range 0..100
 *   "scale": 2           // optional, default 2
 * }
 */
@Component
@RequiredArgsConstructor
public class FixedContributionStrategy implements ContributionStrategy {

    private final ConfigEntryFinder entryFinder;
    private final JsonConfigHelper jsonHelper;

    @Override
    public StrategyKey getType() {
        return StrategyKey.FIXED;
    }

    @Override
    public BigDecimal computeContributionAmount(BigDecimal stakeAmount, Jackpot jackpot) {
        // Guard nulls
        if (stakeAmount == null || jackpot == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Locate matching config entry
        ConfigEntry entry = entryFinder.findContributionEntry(jackpot);
        if (entry == null || jsonHelper.isBlank(entry.getConfigJson())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Parse config JSON
        JsonNode root = jsonHelper.readConfigJsonOrNull(entry.getConfigJson());
        if (root == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Pull fields
        BigDecimal percent = jsonHelper.getDecimal(root, "percent");
        if (percent == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Clamp percent to [0, 100]
        if (percent.compareTo(BigDecimal.ZERO) < 0) percent = BigDecimal.ZERO;
        if (percent.compareTo(new BigDecimal("100")) > 0) percent = new BigDecimal("100");

        int scale = root.hasNonNull("scale") ? root.get("scale").asInt(2) : 2;

        // contribution = stake * percent / 100
        BigDecimal contribution = stakeAmount
                .multiply(percent)
                .divide(new BigDecimal("100"), scale, RoundingMode.HALF_UP);

        return contribution.setScale(scale, RoundingMode.HALF_UP);
    }
}
