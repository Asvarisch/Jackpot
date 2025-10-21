package com.asvarishch.jackpot.repository;

import com.asvarishch.jackpot.model.Jackpot;
import com.asvarishch.jackpot.model.JackpotConfig;
import com.asvarishch.jackpot.model.JackpotReward;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;


@DataJpaTest(properties = {
        // Disable automatic SQL initialization for data.sql
        "spring.sql.init.mode=never",
})
class JackpotRewardRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JackpotRewardRepository rewardRepository;

    // --- helpers ---

    private JackpotConfig persistConfig(String id) {
        JackpotConfig cfg = new JackpotConfig();
        cfg.setJackpotConfigId(id);
        cfg.setName("Cfg " + id);
        em.persist(cfg);
        return cfg;
    }

    private Jackpot persistJackpot(String name, int cycle, JackpotConfig cfg) {
        Jackpot j = new Jackpot();
        j.setName(name);
        j.setInitialAmount(new BigDecimal("10000.00"));
        j.setCurrentAmount(new BigDecimal("12000.00"));
        j.setCycle(cycle);
        j.setVersion(0L);
        j.setJackpotConfig(cfg);
        em.persist(j);
        return j;
    }

    private JackpotReward persistReward(Jackpot jackpot, int cycle, long betId, long userId, BigDecimal amount) {
        JackpotReward r = new JackpotReward();
        r.setJackpot(jackpot);
        r.setJackpotCycle(cycle);
        r.setBetId(betId);
        r.setUserId(userId);
        r.setJackpotRewardAmount(amount);
        em.persist(r);
        return r;
    }

    @Test
    @DisplayName("existsByJackpot_JackpotIdAndJackpotCycle() true when reward exists for that cycle")
    void exists_true() {
        JackpotConfig cfg = persistConfig("fixed-variable");
        Jackpot j = persistJackpot("JP-R", 7, cfg);
        persistReward(j, 7, 30001L, 601L, new BigDecimal("12345.67"));
        em.flush();
        em.clear();

        boolean exists = rewardRepository.existsByJackpot_JackpotIdAndJackpotCycle(j.getJackpotId(), 7);
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByJackpot_JackpotIdAndJackpotCycle() false for empty cycle or different jackpot")
    void exists_false() {
        JackpotConfig cfg = persistConfig("variable-variable");
        Jackpot j1 = persistJackpot("JP-A", 3, cfg);
        Jackpot j2 = persistJackpot("JP-B", 3, cfg);
        // reward only for j1 cycle 3
        persistReward(j1, 3, 30002L, 602L, new BigDecimal("11111.11"));
        em.flush();
        em.clear();

        assertThat(rewardRepository.existsByJackpot_JackpotIdAndJackpotCycle(j1.getJackpotId(), 2)).isFalse();
        assertThat(rewardRepository.existsByJackpot_JackpotIdAndJackpotCycle(j2.getJackpotId(), 3)).isFalse();
    }
}
