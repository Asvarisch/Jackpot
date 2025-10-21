package com.asvarishch.jackpot.strategy.impl;

import com.asvarishch.jackpot.model.ConfigEntry;
import com.asvarishch.jackpot.model.Jackpot;
import com.asvarishch.jackpot.util.ConfigEntryFinder;
import com.asvarishch.jackpot.util.JsonConfigHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Tests for VariableContributionStrategy.
 * Verifies: start/end percent usage and linear interpolation across [fromPool, toPool].
 */
@ExtendWith(MockitoExtension.class)
class VariableContributionStrategyTest {

    @Mock
    private ConfigEntryFinder entryFinder; // mocked dependency

    @Mock
    private JsonConfigHelper jsonHelper;   // mocked dependency

    @InjectMocks
    private VariableContributionStrategy strategy; // SUT

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Jackpot jackpot;

    @BeforeEach
    void setUp() {
        jackpot = org.mockito.Mockito.mock(Jackpot.class);
    }

    /** Helper to produce a real JsonNode so that strategy can read "scale" directly. */
    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Variable: inside range linearly interpolates between startPercent and endPercent")
    void variable_insideRange_linearInterpolation() {
        String cfg = "{\"startPercent\":\"30\",\"endPercent\":\"10\",\"fromPool\":\"1000\",\"toPool\":\"11000\",\"scale\":2}";
        ConfigEntry entry = new ConfigEntry();
        entry.setConfigJson(cfg);

        when(entryFinder.findContributionEntry(jackpot)).thenReturn(entry);
        when(jsonHelper.isBlank(cfg)).thenReturn(false);
        when(jsonHelper.readConfigJsonOrNull(cfg)).thenReturn(json(cfg));

        when(jsonHelper.getDecimal(any(JsonNode.class), eq("startPercent"))).thenReturn(new BigDecimal("30"));
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("endPercent"))).thenReturn(new BigDecimal("10"));
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("fromPool"))).thenReturn(new BigDecimal("1000"));
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("toPool"))).thenReturn(new BigDecimal("11000"));

        // currentPool exactly in the middle: from 1000 to 11000 -> middle is 6000
        when(jackpot.getCurrentAmount()).thenReturn(new BigDecimal("6000"));

        BigDecimal result = strategy.computeContributionAmount(new BigDecimal("200"), jackpot);

        // Interpolated percent = 20% -> 200 * 0.2 = 40.00
        assertEquals(new BigDecimal("40.00"), result);
    }

    @Test
    @DisplayName("Variable: below range uses startPercent")
    void variable_belowRange_usesStartPercent() {
        String cfg = "{\"startPercent\":\"30\",\"endPercent\":\"10\",\"fromPool\":\"1000\",\"toPool\":\"10000\",\"scale\":2}";
        ConfigEntry entry = new ConfigEntry();
        entry.setConfigJson(cfg);

        when(entryFinder.findContributionEntry(jackpot)).thenReturn(entry);
        when(jsonHelper.isBlank(cfg)).thenReturn(false);
        when(jsonHelper.readConfigJsonOrNull(cfg)).thenReturn(json(cfg));

        // Strategy requests decimals via jsonHelper.getDecimal(...)
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("startPercent"))).thenReturn(new BigDecimal("30"));
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("endPercent"))).thenReturn(new BigDecimal("10"));
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("fromPool"))).thenReturn(new BigDecimal("1000"));
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("toPool"))).thenReturn(new BigDecimal("10000"));

        when(jackpot.getCurrentAmount()).thenReturn(new BigDecimal("500")); // below fromPool

        BigDecimal result = strategy.computeContributionAmount(new BigDecimal("200"), jackpot);

        // 30% of 200 = 60.00
        assertEquals(new BigDecimal("60.00"), result);
    }

    @Test
    @DisplayName("Variable: above range uses endPercent")
    void variable_aboveRange_usesEndPercent() {
        String cfg = "{\"startPercent\":\"30\",\"endPercent\":\"10\",\"fromPool\":\"1000\",\"toPool\":\"10000\",\"scale\":2}";
        ConfigEntry entry = new ConfigEntry();
        entry.setConfigJson(cfg);

        when(entryFinder.findContributionEntry(jackpot)).thenReturn(entry);
        when(jsonHelper.isBlank(cfg)).thenReturn(false);
        when(jsonHelper.readConfigJsonOrNull(cfg)).thenReturn(json(cfg));

        when(jsonHelper.getDecimal(any(JsonNode.class), eq("startPercent"))).thenReturn(new BigDecimal("30"));
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("endPercent"))).thenReturn(new BigDecimal("10"));
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("fromPool"))).thenReturn(new BigDecimal("1000"));
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("toPool"))).thenReturn(new BigDecimal("10000"));

        when(jackpot.getCurrentAmount()).thenReturn(new BigDecimal("20000")); // above toPool

        BigDecimal result = strategy.computeContributionAmount(new BigDecimal("150"), jackpot);

        // 10% of 150 = 15.00
        assertEquals(new BigDecimal("15.00"), result);
    }



    @Test
    @DisplayName("Variable: missing or unreadable config returns 0.00")
    void variable_blankOrUnreadableConfig_returnsZero() {
        // blank path
        ConfigEntry blank = new ConfigEntry();
        blank.setConfigJson("");

        when(entryFinder.findContributionEntry(jackpot)).thenReturn(blank);
        when(jsonHelper.isBlank("")).thenReturn(true);

        BigDecimal resBlank = strategy.computeContributionAmount(new BigDecimal("50"), jackpot);
        assertEquals(new BigDecimal("0.00"), resBlank);

        // unreadable JSON path
        String bad = "{oops";
        ConfigEntry entryBad = new ConfigEntry();
        entryBad.setConfigJson(bad);

        when(entryFinder.findContributionEntry(jackpot)).thenReturn(entryBad);
        when(jsonHelper.isBlank(bad)).thenReturn(false);
        when(jsonHelper.readConfigJsonOrNull(bad)).thenReturn(null);

        BigDecimal resBad = strategy.computeContributionAmount(new BigDecimal("50"), jackpot);
        assertEquals(new BigDecimal("0.00"), resBad);
    }
}
