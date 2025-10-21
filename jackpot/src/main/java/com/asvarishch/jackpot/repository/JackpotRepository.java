package com.asvarishch.jackpot.repository;

import com.asvarishch.jackpot.model.Jackpot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JackpotRepository extends JpaRepository<Jackpot, Long> {

    // Find jackpot by ID with eager loading of config
    @Query("""
             SELECT j
             FROM Jackpot j
             LEFT JOIN FETCH j.jackpotConfig
             WHERE j.jackpotId = :jackpotId
            """)
    Optional<Jackpot> findByJackpotIdWithConfig(@Param("jackpotId") Long jackpotId);


    // Lock the single jackpot row when we must finalize a win.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
             SELECT j
             FROM Jackpot j
             WHERE j.jackpotId = :jackpotId
            """)
    Optional<Jackpot> findByIdForUpdate(@Param("jackpotId") Long jackpotId);
}
