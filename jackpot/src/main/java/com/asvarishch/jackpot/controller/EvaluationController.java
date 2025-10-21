package com.asvarishch.jackpot.controller;

import com.asvarishch.jackpot.dto.EvaluateResponseDTO;
import com.asvarishch.jackpot.repository.JackpotRepository;
import com.asvarishch.jackpot.service.JackpotEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * POST /api/bets/{betId}/evaluate
 * Body: { "userId": "...", "jackpotId": "..." }
 * Returns: { jackpotId, betId, payout }
 * <p>
 * Idempotent: if the bet already has a reward, that same reward is returned.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final JackpotEvaluationService jackpotEvaluationService;
    private final JackpotRepository jackpotRepository;

    @GetMapping("/{betId}")
    public ResponseEntity<EvaluateResponseDTO> evaluate(@PathVariable Long betId) {
        final EvaluateResponseDTO response = jackpotEvaluationService.evaluateAndReward(betId);
        return ResponseEntity.ok(response);
    }
}
