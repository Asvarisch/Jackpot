package com.asvarishch.jackpot.repository;

import com.asvarishch.jackpot.model.JackpotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JackpotConfigRepository extends JpaRepository<JackpotConfig, String> {}
