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
 * Tests for FixedContributionStrategy.
 * Verifies: stake * percent / 100 with scale; clamping; blank/unreadable config.
 */
@ExtendWith(MockitoExtension.class)
class FixedContributionStrategyTest {

    @Mock
    private ConfigEntryFinder entryFinder;

    @Mock
    private JsonConfigHelper jsonHelper;

    @InjectMocks
    private FixedContributionStrategy strategy;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Jackpot jackpot;

    @BeforeEach
    void setUp() {
        // Jackpot is not actually used by Fixed strategy, but is required by the signature
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
    @DisplayName("Fixed: computes stake * percent / 100 with given scale")
    void fixed_basicPercentage() {
        // given
        String cfg = "{\"percent\":\"20.0\",\"scale\":2}";
        ConfigEntry entry = new ConfigEntry();
        entry.setConfigJson(cfg);

        when(entryFinder.findContributionEntry(jackpot)).thenReturn(entry);
        when(jsonHelper.isBlank(cfg)).thenReturn(false);
        when(jsonHelper.readConfigJsonOrNull(cfg)).thenReturn(json(cfg));
        // The strategy calls jsonHelper.getDecimal(root, "percent")
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("percent"))).thenReturn(new BigDecimal("20.0"));

        // when
        BigDecimal result = strategy.computeContributionAmount(new BigDecimal("250"), jackpot);

        // then
        assertEquals(new BigDecimal("50.00").setScale(2, RoundingMode.HALF_UP), result);
    }

    @Test
    @DisplayName("Fixed: clamps negative percent to 0% => contribution 0")
    void fixed_negativePercentClampedToZero() {
        String cfg = "{\"percent\":\"-5\",\"scale\":2}";
        ConfigEntry entry = new ConfigEntry();
        entry.setConfigJson(cfg);

        when(entryFinder.findContributionEntry(jackpot)).thenReturn(entry);
        when(jsonHelper.isBlank(cfg)).thenReturn(false);
        when(jsonHelper.readConfigJsonOrNull(cfg)).thenReturn(json(cfg));
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("percent"))).thenReturn(new BigDecimal("-5"));

        BigDecimal result = strategy.computeContributionAmount(new BigDecimal("123.45"), jackpot);

        assertEquals(new BigDecimal("0.00"), result);
    }

    @Test
    @DisplayName("Fixed: clamps percent above 100% to 100% => contribution equals stake (scaled)")
    void fixed_percentAboveHundredClamped() {
        String cfg = "{\"percent\":\"150\",\"scale\":3}";
        ConfigEntry entry = new ConfigEntry();
        entry.setConfigJson(cfg);

        when(entryFinder.findContributionEntry(jackpot)).thenReturn(entry);
        when(jsonHelper.isBlank(cfg)).thenReturn(false);
        when(jsonHelper.readConfigJsonOrNull(cfg)).thenReturn(json(cfg));
        when(jsonHelper.getDecimal(any(JsonNode.class), eq("percent"))).thenReturn(new BigDecimal("150"));

        BigDecimal stake = new BigDecimal("42.5");
        BigDecimal result = strategy.computeContributionAmount(stake, jackpot);

        assertEquals(stake.setScale(3, RoundingMode.HALF_UP), result);
    }

    @Test
    @DisplayName("Fixed: missing or blank config yields 0.00 at default scale 2")
    void fixed_blankConfigReturnsZero() {
        String cfg = "";
        ConfigEntry entry = new ConfigEntry();
        entry.setConfigJson(cfg);

        when(entryFinder.findContributionEntry(jackpot)).thenReturn(entry);
        when(jsonHelper.isBlank(cfg)).thenReturn(true);

        BigDecimal result = strategy.computeContributionAmount(new BigDecimal("100"), jackpot);

        assertEquals(new BigDecimal("0.00"), result);
    }

    @Test
    @DisplayName("Fixed: unreadable JSON or missing node yields 0.00")
    void fixed_unreadableJsonReturnsZero() {
        String cfg = "{not-a-json}";
        ConfigEntry entry = new ConfigEntry();
        entry.setConfigJson(cfg);

        when(entryFinder.findContributionEntry(jackpot)).thenReturn(entry);
        when(jsonHelper.isBlank(cfg)).thenReturn(false);
        when(jsonHelper.readConfigJsonOrNull(cfg)).thenReturn(null); // parsing failed

        BigDecimal result = strategy.computeContributionAmount(new BigDecimal("100"), jackpot);

        assertEquals(new BigDecimal("0.00"), result);
    }
}
