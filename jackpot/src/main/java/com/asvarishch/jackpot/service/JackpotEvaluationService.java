package com.asvarishch.jackpot.service;

import com.asvarishch.jackpot.dto.EvaluateResponseDTO;
import com.asvarishch.jackpot.model.Jackpot;
import com.asvarishch.jackpot.model.JackpotContribution;
import com.asvarishch.jackpot.model.JackpotReward;
import com.asvarishch.jackpot.repository.JackpotContributionRepository;
import com.asvarishch.jackpot.repository.JackpotRepository;
import com.asvarishch.jackpot.repository.JackpotRewardRepository;
import com.asvarishch.jackpot.strategy.RewardStrategy;
import com.asvarishch.jackpot.strategy.StrategyResolver;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static java.math.BigDecimal.ZERO;


/**
 * Service that evaluates a bet against a jackpot and, if it wins, atomically:
 * <ul>
 *   <li>Creates a single reward record for the (jackpot, cycle)</li>
 *   <li>Marks the contribution as winning - just for convenience of validating data in DB</li>
 *   <li>Resets the jackpot to its initial state and increments cycle</li>
 * </ul>
 * <p>
 * <ol>
 *   <li>First, read-only path (no DB locks) to decide if a win is even possible.</li>
 *   <li>Perform a short critical section only when a win must be finalized:
 *       re-read the Jackpot with {@code PESSIMISTIC_WRITE} to guarantee
 *       a single winner per cycle.</li>
 *   <li>Bounded wait for ingestion lag: if the Contribution is not yet visible,
 *       poll briefly with exponential backoff, then return ZERO.</li>
 *   <li>Strict snapshot/cycle fairness checks both before and after the lock.</li>
 *   <li>Idempotency guard: ensure only one reward per (jackpot, cycle).</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JackpotEvaluationService {

    private final JackpotContributionRepository contributionRepository;
    private final JackpotRepository jackpotRepository;
    private final JackpotRewardRepository rewardRepository;
    private final StrategyResolver strategyResolver;
    private final EntityManager entityManager;


    // Await settings for ingestion lag (in case Contribution not yet present)
    private static final long AWAIT_MAX_MS = 3000L;         // total time to wait
    private static final long AWAIT_START_SLEEP_MS = 50L;   // first sleep
    private static final long AWAIT_MAX_SLEEP_MS = 250L;    // cap on backoff sleep

    /**
     * Evaluate a bet and (if it wins) atomically create a single-cycle reward and reset the jackpot.
     * <p>
     * <ol>
     *   <li>Load Contribution (with bounded await if needed). Return ZERO if still not present - caller should retry evaluation request.</li>
     *   <li>Idempotency on the bet: if already winning → ZERO; if already evaluated → ZERO; else mark evaluated=true.</li>
     *   <li>Load Jackpot (with config) for this Contribution. Return ZERO if not found.</li>
     *   <li>Pre-lock fairness: if contributed cycle != current jackpot cycle → ZERO.</li>
     *   <li>Resolve {@link RewardStrategy} and check isWinner on the read jackpot. If lose → ZERO.</li>
     *   <li>Lock jackpot row (PESSIMISTIC_WRITE) and re-validate cycle fairness. If mismatch → ZERO.</li>
     *   <li>Idempotency guard: if reward already exists for (jackpot, cycle) → ZERO.</li>
     *   <li>Create reward, mark contribution as winning, reset jackpot, and return WIN result.</li>
     * </ol>
     *
     * @param betId bet identifier to evaluate
     * @return evaluate response containing payout (ZERO if not a win) and message
     */
    @Transactional
    public EvaluateResponseDTO evaluateAndReward(Long betId) {
        // --- 1) Load contribution (with bounded await) ---
        final Optional<JackpotContribution> contributionOpt = loadContributionOrAwait(betId);
        if (contributionOpt.isEmpty()) {
            log.info("[EVAL] Returning ZERO: pending ingestion after await (~{}ms). betId={}", AWAIT_MAX_MS, betId);
            return zeroResponse("Bet is still being ingested, please retry shortly, betId=", betId, null, null);
        }

        final JackpotContribution contribution = contributionOpt.get();

        // --- 2) Idempotency on the bet itself ---
        final Optional<EvaluateResponseDTO> alreadyProcessed = alreadyProcessedResponse(contribution, betId);
        if (alreadyProcessed.isPresent()) {
            return alreadyProcessed.get();
        }
        // mark as evaluated
        contribution.setEvaluated(true);

        final Long jackpotId = contribution.getJackpot().getJackpotId();
        final Long userId = contribution.getUserId();
        final int contributedCycle = contribution.getJackpotCycle();

        // --- 2) Load Jackpot ---
        final Jackpot jackpot = jackpotRepository.findByJackpotIdWithConfig(jackpotId).orElse(null);
        if (jackpot == null) {
            log.info("[EVAL] Returning ZERO: jackpot not found for jackpotId={}, betId={}", jackpotId, betId);
            return zeroResponse("Jackpot not found: id=" + jackpotId, betId, jackpotId, userId);
        }

        // --- 4) Enforce snapshot/cycle fairness BEFORE any lock ---
        final Optional<EvaluateResponseDTO> preLockFairness = checkCycleFairnessBeforeLock(jackpot, contribution, betId, userId);
        if (preLockFairness.isPresent()) {
            return preLockFairness.get();
        }


        // --- 5) Resolve reward strategy and check win (read path) ---
        final RewardStrategy rewardStrategy = strategyResolver.resolveRewardStrategy(jackpot);
        final boolean isWinner = rewardStrategy.isWinner(jackpot);
        if (!isWinner) {
            log.info("[EVAL] Returning ZERO: betId={} not a winner for jackpotId={}, userId={}", betId, jackpotId, userId);
            return zeroResponse("LOSE: not a winning bet.", betId, jackpotId, userId);
        }

        // --- 6) Lock jackpot (short critical section) and finalize win ---
        return finalizeWinUnderLock(contribution, contributedCycle, betId, userId, jackpotId);
    }


    /**
     * If the contribution is already processed, return the corresponding ZERO response.
     * Otherwise, return empty.
     */
    private Optional<EvaluateResponseDTO> alreadyProcessedResponse(JackpotContribution contribution, Long betId) {
        if (contribution.isWinningContribution()) {
            return Optional.of(
                    zeroResponse(
                            "Bet was already rewarded for a previous win.",
                            betId,
                            contribution.getJackpot().getJackpotId(),
                            contribution.getUserId()
                    )
            );
        }
        if (contribution.isEvaluated()) {
            return Optional.of(
                    zeroResponse(
                            "Bet was already evaluated before.",
                            betId,
                            contribution.getJackpot().getJackpotId(),
                            contribution.getUserId()
                    )
            );
        }
        return Optional.empty();
    }

    /**
     * Enforces snapshot/cycle fairness BEFORE locking.
     * If contributed cycle != current jackpot cycle → ZERO.
     */
    private Optional<EvaluateResponseDTO> checkCycleFairnessBeforeLock(Jackpot jackpot,
                                                                       JackpotContribution contribution,
                                                                       Long betId,
                                                                       Long userId) {
        final int contributedCycle = contribution.getJackpotCycle();
        if (jackpot.getCycle() != contributedCycle) {
            log.info("[EVAL] LOSE: cycle closed before evaluation (contribCycle={}, currentCycle={}). " +
                            "Another bet already won. betId={}, userId={}, jackpotId={}",
                    contributedCycle, jackpot.getCycle(), betId, contribution.getUserId(), contribution.getJackpot().getJackpotId());
            return Optional.of(
                    zeroResponse(
                            "LOSE: cycle closed before evaluation. Another bet already won.",
                            betId,
                            contribution.getJackpot().getJackpotId(),
                            userId
                    )
            );
        }
        return Optional.empty();
    }

    /**
     * Lock jackpot, recheck fairness & idempotency, then create reward, mark contribution and reset jackpot.
     */
    private EvaluateResponseDTO finalizeWinUnderLock(JackpotContribution contribution,
                                                     int contributedCycle,
                                                     Long betId,
                                                     Long userId,
                                                     Long jackpotId) {
        // Locking jackpot row to guarantee single winner per cycle
        final Optional<Jackpot> lockedOpt = jackpotRepository.findByIdForUpdate(jackpotId);
        if (lockedOpt.isEmpty()) {
            // Should not normally happen since earlier we found it, but keep behavior simple
            return zeroResponse("Jackpot not found under lock: id=" + jackpotId, betId, jackpotId, userId);
        }

        final Jackpot lockedJackpot = lockedOpt.get();

        // Re-validate fairness under lock (race guard)
        if (lockedJackpot.getCycle() != contributedCycle) {
            log.info("[EVAL] LOSE: cycle closed before evaluation (contribCycle={}, currentCycle={}). " +
                            "Another bet already won. betId={}, userId={}, jackpotId={}",
                    contributedCycle, lockedJackpot.getCycle(), betId, contribution.getUserId(), jackpotId);
            return zeroResponse(
                    "LOSE: cycle closed before evaluation. Someone else won first.",
                    betId, jackpotId, userId
            );
        }

        // Ensure exactly one reward per (jackpot, cycle)
        final boolean alreadyRewarded = rewardRepository.existsByJackpot_JackpotIdAndJackpotCycle(jackpotId, contributedCycle);
        if (alreadyRewarded) {
            log.info("[EVAL] Returning ZERO: cycle {} already rewarded for jackpotId={}, betId={}",
                    contributedCycle, jackpotId, betId);
            return zeroResponse(
                    "Cycle already has a winner; Winner was already rewarded.",
                    betId, jackpotId, userId
            );
        }

        // Compute payout
        final BigDecimal payout = lockedJackpot.getCurrentAmount();

        // Create reward record
        final JackpotReward reward = JackpotReward.builder()
                .betId(betId)
                .userId(userId)
                .jackpot(lockedJackpot)
                .jackpotRewardAmount(payout)
                .jackpotCycle(contributedCycle)
                .build();
        lockedJackpot.addReward(reward);
        rewardRepository.save(reward);

        contribution.setWinningContribution(true);

        // Reset jackpot under lock
        final int beforeCycle = lockedJackpot.getCycle();
        lockedJackpot.resetToInitial();

        log.info("Jackpot {} WON by betId={}, userId={}, payout={}. Cycle {} -> {} (reset to initial).",
                jackpotId, betId, userId, payout, beforeCycle, lockedJackpot.getCycle());

        // WIN payload
        log.info("[EVAL] Returning WIN result: betId={}, jackpotId={}, userId={}, payout={}",
                betId, jackpotId, userId, payout);
        return EvaluateResponseDTO.builder()
                .betId(betId)
                .jackpotId(jackpotId)
                .userId(userId)
                .payout(payout)
                .message("WIN: payout issued and jackpot reset.")
                .build();
    }


    private EvaluateResponseDTO zeroResponse(String message, Long betId, Long jackpotId, Long userId) {
        return EvaluateResponseDTO.builder()
                .betId(betId)
                .jackpotId(jackpotId)
                .userId(userId)
                .payout(ZERO)
                .message(message)
                .build();
    }

    /**
     * Polls DB for a short, bounded time to let the Contribution appear
     * (covers Kafka/DB ingestion lag). Uses a small exponential backoff.
     */
    private Optional<JackpotContribution> loadContributionOrAwait(long betId) {
        Optional<JackpotContribution> found = contributionRepository.findByBetId(betId);
        if (found.isPresent()) {
            return found;
        }

        long deadlineNanos = System.nanoTime() + AWAIT_MAX_MS * 1_000_000L;
        long sleep = AWAIT_START_SLEEP_MS;

        while (System.nanoTime() < deadlineNanos) {
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            found = contributionRepository.findByBetId(betId);
            if (found.isPresent()) {
                log.info("[EVAL] Contribution appeared after await: betId={}, waited~{}ms",
                        betId, Math.min(AWAIT_MAX_MS, sleep));
                return found;
            }
            // exponential backoff
            sleep = Math.min(sleep * 2, AWAIT_MAX_SLEEP_MS);
        }

        return Optional.empty();
    }
}
