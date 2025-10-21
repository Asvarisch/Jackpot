package com.asvarishch.jackpot.repository;

import com.asvarishch.jackpot.model.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigEntryRepository extends JpaRepository<ConfigEntry, Long> {}
