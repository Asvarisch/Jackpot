package com.asvarishch.jackpot.service;

import com.asvarishch.jackpot.dto.BetEvent;
import com.asvarishch.jackpot.model.Jackpot;
import com.asvarishch.jackpot.model.JackpotConfig;
import com.asvarishch.jackpot.model.JackpotContribution;
import com.asvarishch.jackpot.repository.JackpotContributionRepository;
import com.asvarishch.jackpot.repository.JackpotRepository;
import com.asvarishch.jackpot.strategy.ContributionStrategy;
import com.asvarishch.jackpot.strategy.StrategyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JackpotContributionService tests (aligned with service Javadoc & current flow)")
class JackpotContributionServiceTest {

    @Mock
    private JackpotRepository jackpotRepository;

    @Mock
    private JackpotContributionRepository contributionRepository;

    @Mock
    private StrategyResolver strategyResolver;

    @Mock
    private ContributionStrategy contributionStrategy;

    @Mock
    private BetEvent betEvent;

    private JackpotContributionService service;

    @BeforeEach
    void setUp() {
        service = new JackpotContributionService(jackpotRepository, contributionRepository, strategyResolver);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Builds a Jackpot with non-null collections to match service expectations.
     * Service calls jackpot.addContribution(...) which requires contributions list to be initialized.
     */
    private Jackpot makeJackpot(Long id, String configId, BigDecimal currentAmount, int cycle) {
        JackpotConfig cfg = JackpotConfig.builder()
                .jackpotConfigId(configId)
                .name("cfg-" + configId)
                .build();

        Jackpot jp = Jackpot.builder()
                .jackpotId(id)
                .name("JP-" + id)
                .jackpotConfig(cfg)
                .initialAmount(new BigDecimal("10000.00"))
                .currentAmount(currentAmount)
                .cycle(cycle)
                .version(0L)
                .build();

        // IMPORTANT: initialize collections to avoid NPE in jackpot.addContribution(...)
        jp.setContributions(new ArrayList<>()); // <-- required by service
        jp.setRewards(new ArrayList<>());       // <-- harmless if unused; many models keep it

        return jp;
    }

    private void mockBetEvent(Long betId, Long userId, Long jackpotId, BigDecimal betAmount) {
        when(betEvent.getBetId()).thenReturn(betId);
        when(betEvent.getUserId()).thenReturn(userId);
        when(betEvent.getJackpotId()).thenReturn(jackpotId);
        when(betEvent.getBetAmount()).thenReturn(betAmount);
    }

    // ---------------------------------------------------------------------
    // Happy-paths
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("process() happy-path scenarios")
    class HappyPath {

        @Test
        @DisplayName("Computes contribution, persists it, updates jackpot amount")
        void process_success() {
            // Arrange
            mockBetEvent(1001L, 501L, 1L, new BigDecimal("250.00"));
            Jackpot jackpot = makeJackpot(1L, "fixed-fixed", new BigDecimal("10000.00"), 7);

            when(contributionRepository.findByBetId(1001L)).thenReturn(Optional.empty());
            when(jackpotRepository.findByJackpotIdWithConfig(1L)).thenReturn(Optional.of(jackpot));
            when(strategyResolver.resolveContributionStrategy(jackpot)).thenReturn(contributionStrategy);
            when(contributionStrategy.computeContributionAmount(new BigDecimal("250.00"), jackpot))
                    .thenReturn(new BigDecimal("50.00"));
            when(contributionRepository.save(any(JackpotContribution.class)))
                    .thenAnswer(inv -> inv.getArgument(0)); // echo saved entity

            // Act
            JackpotContribution result = service.contribute(betEvent);

            // Assert entity fields
            assertThat(result.getBetId()).isEqualTo(1001L);
            assertThat(result.getUserId()).isEqualTo(501L);
            assertThat(result.getJackpot().getJackpotId()).isEqualTo(1L);
            assertThat(result.getStakeAmount()).isEqualByComparingTo("250.00");
            assertThat(result.getContributionAmount()).isEqualByComparingTo("50.00");
            assertThat(result.getCurrentJackpotAmount()).isEqualByComparingTo("10000.00");
            assertThat(result.getJackpotCycle()).isEqualTo(7);
            assertThat(result.isWinningContribution()).isFalse();

            // Jackpot updated
            assertThat(jackpot.getCurrentAmount()).isEqualByComparingTo("10050.00");

            // Interaction order (as per Javadoc)
            InOrder inOrder = inOrder(contributionRepository, jackpotRepository, strategyResolver, contributionStrategy);
            inOrder.verify(contributionRepository).findByBetId(1001L);
            inOrder.verify(jackpotRepository).findByJackpotIdWithConfig(1L);
            inOrder.verify(strategyResolver).resolveContributionStrategy(jackpot);
            inOrder.verify(contributionStrategy).computeContributionAmount(new BigDecimal("250.00"), jackpot);
            inOrder.verify(contributionRepository).save(any(JackpotContribution.class));
            inOrder.verify(jackpotRepository).save(jackpot);
        }

        @Test
        @DisplayName("Idempotency: pre-existing bet returns existing row and skips strategy/jackpot paths")
        void idempotent_existing_returned() {
            // Arrange
            mockBetEvent(1002L, 502L, 1L, new BigDecimal("123.45"));
            Jackpot jackpot = makeJackpot(1L, "fixed-fixed", new BigDecimal("9000.00"), 3);

            JackpotContribution existing = JackpotContribution.builder()
                    .contributionId(999L)
                    .betId(1002L)
                    .userId(502L)
                    .jackpot(jackpot)
                    .stakeAmount(new BigDecimal("123.45"))
                    .contributionAmount(new BigDecimal("12.34"))
                    .currentJackpotAmount(new BigDecimal("9000.00"))
                    .jackpotCycle(3)
                    .winningContribution(false)
                    .build();

            when(contributionRepository.findByBetId(1002L)).thenReturn(Optional.of(existing));

            // Act
            JackpotContribution result = service.contribute(betEvent);

            // Assert
            assertThat(result).isSameAs(existing);
            verifyNoInteractions(jackpotRepository, strategyResolver, contributionStrategy);
            verify(contributionRepository, times(1)).findByBetId(1002L);
            verifyNoMoreInteractions(contributionRepository);
        }
    }

    // ---------------------------------------------------------------------
    // Edge/error cases
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("process() edge cases")
    class ErrorCases {

        @Test
        @DisplayName("Jackpot not found => IllegalArgumentException")
        void process_jackpotNotFound() {
            mockBetEvent(1003L, 503L, 999L, new BigDecimal("100.00"));
            when(contributionRepository.findByBetId(1003L)).thenReturn(Optional.empty());
            when(jackpotRepository.findByJackpotIdWithConfig(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.contribute(betEvent))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Jackpot not found");

            verify(contributionRepository).findByBetId(1003L);
            verify(jackpotRepository).findByJackpotIdWithConfig(999L);
            verifyNoMoreInteractions(jackpotRepository, contributionRepository);
            verifyNoInteractions(strategyResolver, contributionStrategy);
        }

        @Test
        @DisplayName("Zero contribution amount is accepted; jackpot is saved with +0 (no change)")
        void zero_contribution_allowed() {
            mockBetEvent(2001L, 601L, 1L, new BigDecimal("250.00"));
            Jackpot jackpot = makeJackpot(1L, "fixed-fixed", new BigDecimal("10000.00"), 7);

            when(contributionRepository.findByBetId(2001L)).thenReturn(Optional.empty());
            when(jackpotRepository.findByJackpotIdWithConfig(1L)).thenReturn(Optional.of(jackpot));
            when(strategyResolver.resolveContributionStrategy(jackpot)).thenReturn(contributionStrategy);
            when(contributionStrategy.computeContributionAmount(new BigDecimal("250.00"), jackpot))
                    .thenReturn(new BigDecimal("0.00"));
            when(contributionRepository.save(any(JackpotContribution.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            JackpotContribution result = service.contribute(betEvent);

            assertThat(result.getContributionAmount()).isEqualByComparingTo("0.00");
            assertThat(jackpot.getCurrentAmount()).isEqualByComparingTo("10000.00"); // unchanged
            verify(jackpotRepository).save(jackpot); // still persisted per current flow
        }

        @Test
        @DisplayName("Concurrent duplicate bet: return existing row (idempotent); jackpot save is allowed by flow")
        void process_concurrentDuplicateBet() {
            mockBetEvent(1005L, 505L, 1L, new BigDecimal("150.00"));
            Jackpot jackpot = makeJackpot(1L, "fixed-fixed", new BigDecimal("8000.00"), 12);

            JackpotContribution existing = JackpotContribution.builder()
                    .contributionId(777L)
                    .betId(1005L)
                    .userId(505L)
                    .jackpot(jackpot)
                    .stakeAmount(new BigDecimal("150.00"))
                    .contributionAmount(new BigDecimal("15.00"))
                    .currentJackpotAmount(new BigDecimal("8000.00"))
                    .jackpotCycle(12)
                    .winningContribution(false)
                    .build();

            // First lookup: empty; second lookup (after integrity violation): existing row
            when(contributionRepository.findByBetId(1005L))
                    .thenReturn(Optional.empty(), Optional.of(existing));

            when(jackpotRepository.findByJackpotIdWithConfig(1L)).thenReturn(Optional.of(jackpot));
            when(strategyResolver.resolveContributionStrategy(jackpot)).thenReturn(contributionStrategy);
            when(contributionStrategy.computeContributionAmount(new BigDecimal("150.00"), jackpot))
                    .thenReturn(new BigDecimal("15.00"));

            // Simulate unique constraint violation on save, then service re-reads by betId
            when(contributionRepository.save(any(JackpotContribution.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate bet_id"));

            JackpotContribution result = service.contribute(betEvent);

            // Return existing row per idempotency rule
            assertThat(result).isSameAs(existing);

            // Verify core interactions; do NOT forbid jackpot save (your flow may still update/persist)
            verify(contributionRepository, times(2)).findByBetId(1005L);
            verify(contributionRepository).save(any(JackpotContribution.class));
            verify(jackpotRepository).findByJackpotIdWithConfig(1L);
            verify(strategyResolver).resolveContributionStrategy(jackpot);
            verify(contributionStrategy).computeContributionAmount(new BigDecimal("150.00"), jackpot);
        }

        @Test
        @DisplayName("Integrity violation without duplicate row present => rethrow DIV exception")
        void duplicate_violation_without_dupe_rethrown() {
            mockBetEvent(2002L, 602L, 1L, new BigDecimal("100.00"));
            Jackpot jackpot = makeJackpot(1L, "fixed-fixed", new BigDecimal("500.00"), 1);

            when(contributionRepository.findByBetId(2002L)).thenReturn(Optional.empty());
            when(jackpotRepository.findByJackpotIdWithConfig(1L)).thenReturn(Optional.of(jackpot));
            when(strategyResolver.resolveContributionStrategy(jackpot)).thenReturn(contributionStrategy);
            when(contributionStrategy.computeContributionAmount(new BigDecimal("100.00"), jackpot))
                    .thenReturn(new BigDecimal("10.00"));

            // First save throws...
            when(contributionRepository.save(any(JackpotContribution.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate?"));
            // ...and second lookup still finds nothing => should rethrow
            when(contributionRepository.findByBetId(2002L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.contribute(betEvent))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // ---------------------------------------------------------------------
    // Input validation (split per field; no lenient needed)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("Null BetEvent => NPE with message")
        void null_event_throws() {
            assertThatThrownBy(() -> service.contribute(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("betEvent must not be null");
        }

        @Test
        @DisplayName("betId <= 0 => IAE")
        void betId_non_positive_throws() {
            when(betEvent.getBetId()).thenReturn(0L);
            assertThatThrownBy(() -> service.contribute(betEvent))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("betId must be positive");
        }

        @Test
        @DisplayName("userId <= 0 => IAE")
        void userId_non_positive_throws() {
            when(betEvent.getBetId()).thenReturn(1L);
            when(betEvent.getUserId()).thenReturn(0L);
            assertThatThrownBy(() -> service.contribute(betEvent))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("userId must be positive");
        }

        @Test
        @DisplayName("jackpotId <= 0 => IAE")
        void jackpotId_non_positive_throws() {
            when(betEvent.getBetId()).thenReturn(1L);
            when(betEvent.getUserId()).thenReturn(1L);
            when(betEvent.getJackpotId()).thenReturn(0L);
            assertThatThrownBy(() -> service.contribute(betEvent))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("jackpotId must be positive");
        }

        @Test
        @DisplayName("betAmount <= 0 => IAE")
        void betAmount_non_positive_throws() {
            when(betEvent.getBetId()).thenReturn(1L);
            when(betEvent.getUserId()).thenReturn(1L);
            when(betEvent.getJackpotId()).thenReturn(1L);
            when(betEvent.getBetAmount()).thenReturn(new BigDecimal("0.00"));
            assertThatThrownBy(() -> service.contribute(betEvent))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("betAmount must be positive");
        }

        @Test
        @DisplayName("Null strategy from resolver should surface as NPE")
        void null_strategy_from_resolver() {
            mockBetEvent(2003L, 603L, 1L, new BigDecimal("50.00"));
            Jackpot jackpot = makeJackpot(1L, "fixed-fixed", new BigDecimal("1000.00"), 2);

            when(contributionRepository.findByBetId(2003L)).thenReturn(Optional.empty());
            when(jackpotRepository.findByJackpotIdWithConfig(1L)).thenReturn(Optional.of(jackpot));
            when(strategyResolver.resolveContributionStrategy(jackpot)).thenReturn(null);

            assertThatThrownBy(() -> service.contribute(betEvent))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
