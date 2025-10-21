package com.asvarishch.jackpot.util;

import com.asvarishch.jackpot.enums.Slot;
import com.asvarishch.jackpot.model.ConfigEntry;
import com.asvarishch.jackpot.model.Jackpot;
import com.asvarishch.jackpot.model.JackpotConfig;
import org.springframework.stereotype.Component;

import java.util.Objects;


@Component
public class ConfigEntryFinder {

    public ConfigEntry findContributionEntry(Jackpot jackpot) {
        JackpotConfig cfg = requireConfig(jackpot);
        return cfg.getEntries().stream()
                .filter(e -> e.getSlot() == Slot.CONTRIBUTION)
                .findFirst()
                .orElse(null);
    }

    public ConfigEntry findRewardEntry(Jackpot jackpot) {
        JackpotConfig cfg = requireConfig(jackpot);
        return cfg.getEntries().stream()
                .filter(e -> e.getSlot() == Slot.REWARD)
                .findFirst()
                .orElse(null);
    }


    private JackpotConfig requireConfig(Jackpot jackpot) {
        Objects.requireNonNull(jackpot, "jackpot must not be null");
        JackpotConfig cfg = jackpot.getJackpotConfig();
        if (cfg == null || cfg.getEntries() == null) {
            throw new IllegalStateException("JackpotConfig or entries are null for jackpotId=" + jackpot.getJackpotId());
        }
        return cfg;
    }
}
