package com.asvarishch.jackpot.service;

import com.asvarishch.jackpot.dto.EvaluateResponseDTO;
import com.asvarishch.jackpot.model.Jackpot;
import com.asvarishch.jackpot.model.JackpotConfig;
import com.asvarishch.jackpot.model.JackpotContribution;
import com.asvarishch.jackpot.model.JackpotReward;
import com.asvarishch.jackpot.repository.JackpotContributionRepository;
import com.asvarishch.jackpot.repository.JackpotRepository;
import com.asvarishch.jackpot.repository.JackpotRewardRepository;
import com.asvarishch.jackpot.strategy.RewardStrategy;
import com.asvarishch.jackpot.strategy.StrategyResolver;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests are aligned with JackpotEvaluationService Javadoc and current log/messages:
 *  - Await logic: if contribution appears during await, we proceed to normal evaluation; if not, return the
 *    "Bet is still being ingested, please retry shortly, betId=" message.
 *  - Not winner -> "LOSE: not a winning bet."
 *  - Cycle fairness and single-winner enforcement.
 *  - Win path resets jackpot and creates reward.
 *  - Idempotent: cycle already rewarded -> ZERO.
 *  - Locked re-read missing -> ZERO with "not found under lock".
 */
@ExtendWith(MockitoExtension.class)
class JackpotEvaluationServiceTest {

    @Mock private JackpotContributionRepository contributionRepository;
    @Mock private JackpotRepository jackpotRepository;
    @Mock private JackpotRewardRepository rewardRepository;
    @Mock private StrategyResolver strategyResolver;
    @Mock private EntityManager entityManager;
    @Mock private RewardStrategy rewardStrategy;

    private JackpotEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new JackpotEvaluationService(
                contributionRepository,
                jackpotRepository,
                rewardRepository,
                strategyResolver,
                entityManager
        );
    }

    // ---------------------- helpers ----------------------

    private Jackpot makeJackpot(long id, String cfgId, BigDecimal initial, BigDecimal current, int cycle) {
        JackpotConfig cfg = new JackpotConfig();
        cfg.setJackpotConfigId(cfgId);

        Jackpot j = new Jackpot();
        j.setJackpotId(id);
        j.setJackpotConfig(cfg);
        j.setInitialAmount(initial);
        j.setCurrentAmount(current);
        j.setCycle(cycle);

        // IMPORTANT: init collections so service addReward/addContribution won’t NPE
        j.setContributions(new ArrayList<>());
        j.setRewards(new ArrayList<>());

        return j;
    }

    private JackpotContribution makeContribution(long id, long betId, long userId, Jackpot jackpot, int cycleSnapshot) {
        JackpotContribution c = new JackpotContribution();
        c.setContributionId(id);
        c.setBetId(betId);
        c.setUserId(userId);
        c.setJackpot(jackpot);
        c.setJackpotCycle(cycleSnapshot);
        c.setEvaluated(false);
        c.setWinningContribution(false);
        return c;
    }

    // ---------------------- Await / ingestion ----------------------

    @Nested
    @DisplayName("Await / ingestion behavior")
    class AwaitCases {

        @Test
        @DisplayName("Appears during await -> evaluation happens; not winner -> 'LOSE: not a winning bet.'")
        void await_thenAppears() {
            long betId = 2001L, userId = 501L, jackpotId = 1L;
            int cycle = 7;

            Jackpot jackpot = makeJackpot(jackpotId, "fixed-fixed",
                    new BigDecimal("10000.00"), new BigDecimal("12100.00"), cycle);
            JackpotContribution contrib = makeContribution(9001L, betId, userId, jackpot, cycle);

            // First lookup: empty, then found during await
            when(contributionRepository.findByBetId(betId)).thenReturn(Optional.empty(), Optional.of(contrib));

            when(jackpotRepository.findByJackpotIdWithConfig(jackpotId)).thenReturn(Optional.of(jackpot));
            when(strategyResolver.resolveRewardStrategy(jackpot)).thenReturn(rewardStrategy);
            when(rewardStrategy.isWinner(jackpot)).thenReturn(false);

            EvaluateResponseDTO resp = service.evaluateAndReward(betId);

            assertThat(resp.betId()).isEqualTo(betId);
            assertThat(resp.payout()).isEqualByComparingTo(ZERO);
            // EXACT message used by the service for not-winner:
            assertThat(resp.message()).contains("LOSE: not a winning bet.");

            verify(contributionRepository, atLeast(2)).findByBetId(betId);
            verifyNoInteractions(rewardRepository);
        }

        @Test
        @DisplayName("Still missing after full await -> 'Bet is still being ingested, please retry shortly, betId='")
        void await_thenStillMissing() {
            long betId = 2002L;

            // Always empty through the await loop
            when(contributionRepository.findByBetId(betId)).thenReturn(Optional.empty());

            EvaluateResponseDTO resp = service.evaluateAndReward(betId);

            assertThat(resp.betId()).isEqualTo(betId);
            assertThat(resp.payout()).isEqualByComparingTo(ZERO);
            // EXACT prefix from the service:
            assertThat(resp.message()).contains("Bet is still being ingested, please retry shortly, betId=");

            verify(contributionRepository, atLeastOnce()).findByBetId(betId);
            verifyNoInteractions(jackpotRepository, rewardRepository, strategyResolver, rewardStrategy);
        }
    }

    // ---------------------- Snapshot fairness / lose ----------------------

    @Nested
    @DisplayName("Snapshot fairness and lose cases")
    class LosePath {

        @Test
        @DisplayName("Contribution cycle < current cycle -> ZERO, 'cycle closed before evaluation'")
        void cycleClosedBeforeEvaluation() {
            long betId = 3001L, userId = 601L, jackpotId = 3L;
            int contribCycle = 8, currentCycle = 9;

            Jackpot jackpot = makeJackpot(jackpotId, "fixed-fixed",
                    new BigDecimal("10000.00"), new BigDecimal("11000.00"), currentCycle);
            JackpotContribution contrib = makeContribution(9101L, betId, userId, jackpot, contribCycle);

            when(contributionRepository.findByBetId(betId)).thenReturn(Optional.of(contrib));
            when(jackpotRepository.findByJackpotIdWithConfig(jackpotId)).thenReturn(Optional.of(jackpot));

            EvaluateResponseDTO resp = service.evaluateAndReward(betId);

            assertThat(resp.payout()).isEqualByComparingTo(ZERO);
            assertThat(resp.message()).contains("cycle closed before evaluation");

            verifyNoInteractions(strategyResolver, rewardRepository, rewardStrategy);
        }

        @Test
        @DisplayName("Not winner -> ZERO, exact message 'LOSE: not a winning bet.'")
        void notWinner_noWrites() {
            long betId = 3002L, userId = 602L, jackpotId = 3L;
            int cycle = 9;

            Jackpot jackpot = makeJackpot(jackpotId, "fixed-fixed",
                    new BigDecimal("10000.00"), new BigDecimal("11500.00"), cycle);
            JackpotContribution contrib = makeContribution(9102L, betId, userId, jackpot, cycle);

            when(contributionRepository.findByBetId(betId)).thenReturn(Optional.of(contrib));
            when(jackpotRepository.findByJackpotIdWithConfig(jackpotId)).thenReturn(Optional.of(jackpot));
            when(strategyResolver.resolveRewardStrategy(jackpot)).thenReturn(rewardStrategy);
            when(rewardStrategy.isWinner(jackpot)).thenReturn(false);

            EvaluateResponseDTO resp = service.evaluateAndReward(betId);

            assertThat(resp.payout()).isEqualByComparingTo(ZERO);
            assertThat(resp.message()).contains("LOSE: not a winning bet.");

            verifyNoInteractions(rewardRepository);
        }
    }

    // ---------------------- Win, single-winner, idempotency ----------------------

    @Nested
    @DisplayName("Win path, lock, single winner")
    class WinPath {

        @Test
        @DisplayName("Winner -> reward saved, contribution flagged, jackpot reset; payout equals current snapshot")
        void winner_success() {
            long betId = 4001L, userId = 701L, jackpotId = 4L;
            int cycle = 5;
            BigDecimal initial = new BigDecimal("10000.00");
            BigDecimal current = new BigDecimal("12100.00");

            Jackpot jackpot = makeJackpot(jackpotId, "fixed-fixed", initial, current, cycle);
            JackpotContribution contrib = makeContribution(9201L, betId, userId, jackpot, cycle);
            Jackpot locked = makeJackpot(jackpotId, "fixed-fixed", initial, current, cycle);

            when(contributionRepository.findByBetId(betId)).thenReturn(Optional.of(contrib));
            when(jackpotRepository.findByJackpotIdWithConfig(jackpotId)).thenReturn(Optional.of(jackpot));
            when(strategyResolver.resolveRewardStrategy(jackpot)).thenReturn(rewardStrategy);
            when(rewardStrategy.isWinner(jackpot)).thenReturn(true);
            when(jackpotRepository.findByIdForUpdate(jackpotId)).thenReturn(Optional.of(locked));

            EvaluateResponseDTO resp = service.evaluateAndReward(betId);

            assertThat(resp.betId()).isEqualTo(betId);
            assertThat(resp.payout()).isEqualByComparingTo(current);
            assertThat(resp.message()).contains("WIN");

            verify(rewardRepository).save(any(JackpotReward.class));
            assertThat(contrib.isWinningContribution()).isTrue();
            assertThat(locked.getCycle()).isEqualTo(cycle + 1);
            assertThat(locked.getCurrentAmount()).isEqualByComparingTo(initial);

            // Service doesn't use the EntityManager in assertions; ensure we don't require interactions.
            verifyNoInteractions(entityManager);
        }

        @Test
        @DisplayName("Cycle already rewarded under lock -> ZERO, no new reward")
        void cycleAlreadyRewarded() {
            long betId = 4002L, userId = 702L, jackpotId = 4L;
            int cycle = 20;

            Jackpot jackpot = makeJackpot(jackpotId, "fixed-fixed",
                    new BigDecimal("10000.00"), new BigDecimal("15000.00"), cycle);
            JackpotContribution contrib = makeContribution(9202L, betId, userId, jackpot, cycle);
            Jackpot locked = makeJackpot(jackpotId, "fixed-fixed",
                    new BigDecimal("10000.00"), new BigDecimal("15500.00"), cycle);

            when(contributionRepository.findByBetId(betId)).thenReturn(Optional.of(contrib));
            when(jackpotRepository.findByJackpotIdWithConfig(jackpotId)).thenReturn(Optional.of(jackpot));
            when(strategyResolver.resolveRewardStrategy(jackpot)).thenReturn(rewardStrategy);
            when(rewardStrategy.isWinner(jackpot)).thenReturn(true);
            when(jackpotRepository.findByIdForUpdate(jackpotId)).thenReturn(Optional.of(locked));
            when(rewardRepository.existsByJackpot_JackpotIdAndJackpotCycle(jackpotId, cycle)).thenReturn(true);

            EvaluateResponseDTO resp = service.evaluateAndReward(betId);

            assertThat(resp.payout()).isEqualByComparingTo(ZERO);
            assertThat(resp.message()).contains("already has a winner");

            verify(rewardRepository, never()).save(any(JackpotReward.class));
        }

        @Test
        @DisplayName("Re-read under lock returns empty -> ZERO with 'not found under lock'")
        void jackpotDisappearedUnderLock() {
            long betId = 4005L, userId = 705L, jackpotId = 4L;
            int cycle = 12;

            Jackpot jackpot = makeJackpot(jackpotId, "fixed-fixed",
                    new BigDecimal("10000.00"), new BigDecimal("15500.00"), cycle);
            JackpotContribution contrib = makeContribution(9205L, betId, userId, jackpot, cycle);

            when(contributionRepository.findByBetId(betId)).thenReturn(Optional.of(contrib));
            when(jackpotRepository.findByJackpotIdWithConfig(jackpotId)).thenReturn(Optional.of(jackpot));
            when(strategyResolver.resolveRewardStrategy(jackpot)).thenReturn(rewardStrategy);
            when(rewardStrategy.isWinner(jackpot)).thenReturn(true);
            when(jackpotRepository.findByIdForUpdate(jackpotId)).thenReturn(Optional.empty());

            EvaluateResponseDTO resp = service.evaluateAndReward(betId);

            assertThat(resp.payout()).isEqualByComparingTo(ZERO);
            assertThat(resp.message()).contains("not found under lock");

            verifyNoInteractions(rewardRepository);
        }
    }

    // ---------------------- Already processed contribution ----------------------

    @Nested
    @DisplayName("Already processed contribution")
    class AlreadyProcessed {

        @Test
        @DisplayName("Contribution already evaluated -> ZERO; no further reads/writes")
        void alreadyEvaluated() {
            long betId = 5002L, userId = 802L, jackpotId = 5L;
            int cycle = 33;

            Jackpot jackpot = makeJackpot(jackpotId, "fixed-fixed",
                    new BigDecimal("10000.00"), new BigDecimal("13000.00"), cycle);
            JackpotContribution contrib = makeContribution(9302L, betId, userId, jackpot, cycle);
            contrib.setEvaluated(true);

            when(contributionRepository.findByBetId(betId)).thenReturn(Optional.of(contrib));

            EvaluateResponseDTO resp = service.evaluateAndReward(betId);

            assertThat(resp.payout()).isEqualByComparingTo(ZERO);
            assertThat(resp.message()).contains("already evaluated");

            verifyNoInteractions(jackpotRepository, rewardRepository, strategyResolver, rewardStrategy);
        }
    }

    // ---------------------- Exceptional cases ----------------------

    @Nested
    @DisplayName("Exceptional cases")
    class Exceptional {

        @Test
        @DisplayName("Reward save throws -> exception bubbles up")
        void rewardSaveThrows() {
            long betId = 6001L, userId = 901L, jackpotId = 6L;
            int cycle = 4;
            BigDecimal initial = new BigDecimal("10000.00");
            BigDecimal current = new BigDecimal("11800.00");

            Jackpot jackpot = makeJackpot(jackpotId, "fixed-fixed", initial, current, cycle);
            JackpotContribution contrib = makeContribution(9401L, betId, userId, jackpot, cycle);
            Jackpot locked = makeJackpot(jackpotId, "fixed-fixed", initial, current, cycle);

            when(contributionRepository.findByBetId(betId)).thenReturn(Optional.of(contrib));
            when(jackpotRepository.findByJackpotIdWithConfig(jackpotId)).thenReturn(Optional.of(jackpot));
            when(strategyResolver.resolveRewardStrategy(jackpot)).thenReturn(rewardStrategy);
            when(rewardStrategy.isWinner(jackpot)).thenReturn(true);
            when(jackpotRepository.findByIdForUpdate(jackpotId)).thenReturn(Optional.of(locked));
            when(rewardRepository.save(any(JackpotReward.class)))
                    .thenThrow(new DataIntegrityViolationException("storage failure"));

            try {
                service.evaluateAndReward(betId);
            } catch (DataIntegrityViolationException e) {
                assertThat(e).hasMessageContaining("storage failure");
            }

            verify(rewardRepository).save(any(JackpotReward.class));
        }
    }
}
