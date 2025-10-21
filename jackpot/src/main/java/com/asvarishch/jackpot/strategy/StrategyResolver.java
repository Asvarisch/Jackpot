package com.asvarishch.jackpot.strategy;

import com.asvarishch.jackpot.util.ConfigEntryFinder;
import com.asvarishch.jackpot.enums.StrategyKey;
import com.asvarishch.jackpot.model.ConfigEntry;
import com.asvarishch.jackpot.model.Jackpot;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Component
public class StrategyResolver {

    private final ConfigEntryFinder entryFinder;
    private final Map<StrategyKey, ContributionStrategy> contributionByKey;
    private final Map<StrategyKey, RewardStrategy> rewardByKey;

    public StrategyResolver(
            ConfigEntryFinder entryFinder,
            List<ContributionStrategy> contributions,
            List<RewardStrategy> rewards
    ) {
        this.entryFinder = entryFinder;
        this.contributionByKey = indexContribution(contributions);
        this.rewardByKey = indexReward(rewards);
    }

    /** Resolve a ContributionStrategy from the jackpot's DB config (slot=CONTRIBUTION). */
    public ContributionStrategy resolveContributionStrategy(Jackpot jackpot) {
        ConfigEntry entry = entryFinder.findContributionEntry(jackpot);
        if (entry == null) {
            throw new IllegalStateException("No CONTRIBUTION ConfigEntry found for jackpotId=" + safeId(jackpot));
        }
        ContributionStrategy strategy = contributionByKey.get(entry.getStrategyKey());
        if (strategy == null) {
            throw new IllegalArgumentException("No ContributionStrategy bean for strategyKey=" + entry.getStrategyKey());
        }
        return strategy;
    }

    public RewardStrategy resolveRewardStrategy(Jackpot jackpot) {
        ConfigEntry entry = entryFinder.findRewardEntry(jackpot);
        if (entry == null) {
            throw new IllegalStateException("No REWARD ConfigEntry found for jackpotId=" + safeId(jackpot));
        }
        RewardStrategy strategy = rewardByKey.get(entry.getStrategyKey());
        if (strategy == null) {
            throw new IllegalArgumentException("No RewardStrategy bean for strategyKey=" + entry.getStrategyKey());
        }
        return strategy;
    }

    private static Map<StrategyKey, ContributionStrategy> indexContribution(List<ContributionStrategy> beans) {
        EnumMap<StrategyKey, ContributionStrategy> map = new EnumMap<>(StrategyKey.class);
        for (ContributionStrategy s : beans) {
            StrategyKey k = Objects.requireNonNull(s.getType(), s.getClass().getName() + " returned null getType()");
            if (map.putIfAbsent(k, s) != null) {
                throw new IllegalStateException("Duplicate ContributionStrategy for key=" + k);
            }
        }
        return map;
    }

    private static Map<StrategyKey, RewardStrategy> indexReward(List<RewardStrategy> beans) {
        EnumMap<StrategyKey, RewardStrategy> map = new EnumMap<>(StrategyKey.class);
        for (RewardStrategy s : beans) {
            StrategyKey k = Objects.requireNonNull(s.getType(), s.getClass().getName() + " returned null getType()");
            if (map.putIfAbsent(k, s) != null) {
                throw new IllegalStateException("Duplicate RewardStrategy for key=" + k);
            }
        }
        return map;
    }

    private static String safeId(Jackpot jackpot) {
        return jackpot != null ? String.valueOf(jackpot.getJackpotId()) : "null";
    }
}
