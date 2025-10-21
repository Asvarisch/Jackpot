package com.asvarishch.jackpot.service;

import com.asvarishch.jackpot.dto.BetEvent;
import com.asvarishch.jackpot.model.Jackpot;
import com.asvarishch.jackpot.model.JackpotContribution;
import com.asvarishch.jackpot.repository.JackpotContributionRepository;
import com.asvarishch.jackpot.repository.JackpotRepository;
import com.asvarishch.jackpot.strategy.ContributionStrategy;
import com.asvarishch.jackpot.strategy.StrategyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles recording a player's bet contribution into a jackpot.
 * <p>
 * <ol>
 *   <li>Validate the incoming {@link BetEvent} (all ids and amount must be positive).</li>
 *   <li>Idempotency check: if a contribution for the same {@code betId} already exists → return it.</li>
 *   <li>Load {@link Jackpot} (with config) by {@code jackpotId}; fail if missing.</li>
 *   <li>Resolve {@link ContributionStrategy} via {@link StrategyResolver} (DB-backed).</li>
 *   <li>Compute {@code contributionAmount} from bet amount and jackpot; fail if negative.</li>
 *   <li>Snapshot current pool (BEFORE update) for fairness/audit.</li>
 *   <li>Persist a {@link JackpotContribution} row (capturing snapshot and cycle).
 *       <ul>
 *           <li>On {@link DataIntegrityViolationException} due to concurrent duplicate bet insertion,
 *               re-read by {@code betId} and return the existing row (idempotent behavior).</li>
 *       </ul>
 *   </li>
 *   <li>Increase jackpot's current amount by {@code contributionAmount} and save (optimistic locking via {@code @Version}).</li>
 *   <li>Log the full contribution details; return the saved contribution.</li>
 * </ol>
 * <p>
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class JackpotContributionService {

    private final JackpotRepository jackpotRepository;
    private final JackpotContributionRepository contributionRepository;
    private final StrategyResolver strategyResolver;


    /**
     * Records a bet's contribution into a jackpot with a snapshot of the pool before the update,
     * and updates the jackpot's current amount using optimistic locking.
     *
     * @param betEvent the incoming bet event (non-null, all ids/amount positive)
     * @return the persisted {@link JackpotContribution}, or the existing one if the bet was already processed
     * @throws IllegalArgumentException if jackpot is missing or input values are invalid
     * @throws IllegalStateException    if strategy computes a negative contribution
     */
    @Transactional
    public JackpotContribution contribute(BetEvent betEvent) {
        // --- 1) Validate input ---
        validateBetEvent(betEvent);
        final Long betId = betEvent.getBetId();
        final Long userId = betEvent.getUserId();
        final Long jackpotId = betEvent.getJackpotId();
        final BigDecimal stakeAmount = betEvent.getBetAmount();

        // --- 2) Idempotency: return existing if we already processed this bet ---
        final Optional<JackpotContribution> existing = contributionRepository.findByBetId(betId);
        if (existing.isPresent()) {
            log.warn("Bet {} already contributed; returning existing contribution id={}", betId, existing.get().getContributionId());
            return existing.get();
        }

        // --- 3) Load jackpot (with config) ---
        final Jackpot jackpot = loadJackpotWithConfig(jackpotId);

        // --- 4) Resolve contribution strategy ---
        final ContributionStrategy strategy = strategyResolver.resolveContributionStrategy(jackpot);

        // --- 5) Compute contribution amount ---
        final BigDecimal contributionAmount = computeContributionAmountOrThrow(strategy, stakeAmount, jackpot, betId);

        // --- 6) Snapshot pool BEFORE update (fairness/audit) ---
        final BigDecimal poolBefore = jackpot.getCurrentAmount();

        // --- 7) Persist contribution row (idempotent on duplicate via DB constraint) ---
        final JackpotContribution saved = saveContributionIdempotent(betId, userId, jackpot, stakeAmount, contributionAmount, poolBefore);

        // --- 8) Update the jackpot pool with optimistic locking ---
        updateJackpotPoolAndSave(jackpot, poolBefore, contributionAmount);

        // --- 9) Log the result and return ---
        logContributionProcessed(betId, jackpotId, userId, stakeAmount, contributionAmount, poolBefore, jackpot);
        return saved;
    }



    private static void validateBetEvent(BetEvent betEvent) {
        // Keep exact validation intent, just encapsulated
        Objects.requireNonNull(betEvent, "betEvent must not be null");
        requirePositive("betId", betEvent.getBetId());
        requirePositive("userId", betEvent.getUserId());
        requirePositive("jackpotId", betEvent.getJackpotId());
        requirePositive("betAmount", betEvent.getBetAmount());
    }

    private static void requirePositive(String field, Number n) {
        if (n == null || n.longValue() <= 0L) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }


    private Jackpot loadJackpotWithConfig(Long jackpotId) {
        return jackpotRepository.findByJackpotIdWithConfig(jackpotId)
                .orElseThrow(() -> new IllegalArgumentException("Jackpot not found: id=" + jackpotId));
    }

    private BigDecimal computeContributionAmountOrThrow(ContributionStrategy strategy,
                                                        BigDecimal stakeAmount,
                                                        Jackpot jackpot,
                                                        Long betId) {
        final BigDecimal contributionAmount = strategy.computeContributionAmount(stakeAmount, jackpot);
        if (contributionAmount.signum() < 0) {
            throw new IllegalStateException("Computed negative contribution for betId=" + betId);
        }
        return contributionAmount;
    }


    private JackpotContribution saveContributionIdempotent(Long betId,
                                                           Long userId,
                                                           Jackpot jackpot,
                                                           BigDecimal stakeAmount,
                                                           BigDecimal contributionAmount,
                                                           BigDecimal poolBefore) {
        final JackpotContribution contribution = JackpotContribution.builder()
                .betId(betId)
                .userId(userId)
                .jackpot(jackpot)
                .stakeAmount(stakeAmount)
                .contributionAmount(contributionAmount)
                .currentJackpotAmount(poolBefore)
                .jackpotCycle(jackpot.getCycle())
                .winningContribution(false)
                .build();

        try {
            jackpot.addContribution(contribution); 
            return contributionRepository.save(contribution);
        } catch (DataIntegrityViolationException e) {
            // In case of a concurrent duplicate (unique bet_id), return existing to keep idempotency
            final Optional<JackpotContribution> duplicate = contributionRepository.findByBetId(betId);
            if (duplicate.isPresent()) {
                log.warn("Concurrent duplicate contribution detected for betId={}, returning existing id={}",
                        betId, duplicate.get().getContributionId());
                return duplicate.get();
            }
            throw e;
        }
    }
    
    private void updateJackpotPoolAndSave(Jackpot jackpot, BigDecimal poolBefore, BigDecimal contributionAmount) {
        jackpot.setCurrentAmount(poolBefore.add(contributionAmount));
        jackpotRepository.save(jackpot);
    }

    private static void logContributionProcessed(Long betId,
                                                 Long jackpotId,
                                                 Long userId,
                                                 BigDecimal stakeAmount,
                                                 BigDecimal contributionAmount,
                                                 BigDecimal poolBefore,
                                                 Jackpot jackpot) {
        log.info("Contribution processed: betId={}, jackpotId={}, userId={}, stake={}, contrib={}, poolBefore={}, poolAfter={}, cycle={}",
                betId, jackpotId, userId, stakeAmount, contributionAmount, poolBefore, jackpot.getCurrentAmount(), jackpot.getCycle());
    }
}
