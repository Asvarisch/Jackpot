package com.asvarishch.jackpot.repository;

import com.asvarishch.jackpot.model.JackpotReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface JackpotRewardRepository extends JpaRepository<JackpotReward, Long> {

    boolean existsByJackpot_JackpotIdAndJackpotCycle(Long jackpotId, int jackpotCycle);

}
