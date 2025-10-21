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
 * Variable contribution (linear interpolation):
 *   - Percent starts high and decreases as the pool grows, until it reaches a floor.
 *   - Interpolated linearly across a pool interval [fromPool .. toPool].
 *
 * Expected JSON in ConfigEntry.configJson:
 * {
 *   "startPercent": "30.0",   // required (0..100) at pool <= fromPool
 *   "endPercent":   "10.0",   // required (0..100) at pool >= toPool
 *   "fromPool":     "0.00",   // optional; default 0.00
 *   "toPool":       "100000", // required; if toPool <= fromPool => treat as fixed startPercent
 *   "scale":        2         // optional; default 2
 * }
 */
@Component
@RequiredArgsConstructor
public class VariableContributionStrategy implements ContributionStrategy {

    private final ConfigEntryFinder entryFinder;
    private final JsonConfigHelper jsonHelper;

    @Override
    public StrategyKey getType() {
        return StrategyKey.VARIABLE;
    }

    @Override
    public BigDecimal computeContributionAmount(BigDecimal stakeAmount, Jackpot jackpot) {
        if (stakeAmount == null || jackpot == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        ConfigEntry entry = entryFinder.findContributionEntry(jackpot);
        if (entry == null || jsonHelper.isBlank(entry.getConfigJson())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        JsonNode root = jsonHelper.readConfigJsonOrNull(entry.getConfigJson());
        if (root == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal startPct = jsonHelper.getDecimal(root, "startPercent");
        BigDecimal endPct   = jsonHelper.getDecimal(root, "endPercent");
        if (startPct == null || endPct == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Clamp to [0,100]
        if (startPct.compareTo(BigDecimal.ZERO) < 0) startPct = BigDecimal.ZERO;
        if (startPct.compareTo(new BigDecimal("100")) > 0) startPct = new BigDecimal("100");
        if (endPct.compareTo(BigDecimal.ZERO) < 0) endPct = BigDecimal.ZERO;
        if (endPct.compareTo(new BigDecimal("100")) > 0) endPct = new BigDecimal("100");

        BigDecimal fromPool = jsonHelper.getDecimal(root, "fromPool");
        if (fromPool == null || fromPool.compareTo(BigDecimal.ZERO) < 0) fromPool = BigDecimal.ZERO;

        BigDecimal toPool = jsonHelper.getDecimal(root, "toPool");
        int scale = root.hasNonNull("scale") ? root.get("scale").asInt(2) : 2;

        BigDecimal currentPool = jackpot.getCurrentAmount() == null ? BigDecimal.ZERO : jackpot.getCurrentAmount();

        BigDecimal effectivePct;
        if (toPool == null || toPool.compareTo(fromPool) <= 0) {
            effectivePct = startPct;
        } else {
            // Interpolate over [fromPool..toPool]
            if (currentPool.compareTo(fromPool) <= 0) {
                effectivePct = startPct;
            } else if (currentPool.compareTo(toPool) >= 0) {
                effectivePct = endPct;
            } else {
                BigDecimal range = toPool.subtract(fromPool);
                BigDecimal pos   = currentPool.subtract(fromPool);
                BigDecimal fraction = pos.divide(range, 8, RoundingMode.HALF_UP);
                effectivePct = startPct.add(endPct.subtract(startPct).multiply(fraction));
            }
        }

        BigDecimal contribution = stakeAmount
                .multiply(effectivePct)
                .divide(new BigDecimal("100"), scale, RoundingMode.HALF_UP);

        return contribution.setScale(scale, RoundingMode.HALF_UP);
    }
}
