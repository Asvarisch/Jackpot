package com.asvarishch.jackpot.repository;

import com.asvarishch.jackpot.model.Jackpot;
import com.asvarishch.jackpot.model.JackpotConfig;
import com.asvarishch.jackpot.model.JackpotContribution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        // Disable automatic SQL initialization for data.sql
        "spring.sql.init.mode=never"
})
class JackpotContributionRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JackpotContributionRepository contributionRepository;

    // --- helpers ---

    private JackpotConfig persistConfig(String id) {
        JackpotConfig cfg = new JackpotConfig();
        cfg.setJackpotConfigId(id);
        cfg.setName("Cfg " + id);
        em.persist(cfg);
        return cfg;
    }

    private Jackpot persistJackpot(long idHint, JackpotConfig cfg) {
        Jackpot j = new Jackpot();
        j.setName("JP-" + idHint);
        j.setInitialAmount(new BigDecimal("10000.00"));
        j.setCurrentAmount(new BigDecimal("11500.00"));
        j.setCycle(2);
        j.setJackpotConfig(cfg);
        em.persist(j);
        return j;
    }

    private JackpotContribution persistContribution(
            long betId, long userId, Jackpot jackpot, int cycleSnapshot,
            BigDecimal stakeAmount, BigDecimal contributed, BigDecimal currentJackpotSnapshot) {

        JackpotContribution c = new JackpotContribution();
        c.setBetId(betId);
        c.setUserId(userId);
        c.setJackpot(jackpot);
        c.setJackpotCycle(cycleSnapshot);
        c.setWinningContribution(false);

        // REQUIRED NOT NULL FIELDS
        c.setStakeAmount(stakeAmount);                       // stake_amount NOT NULL
        c.setContributionAmount(contributed);                // contribution_amount NOT NULL
        c.setCurrentJackpotAmount(currentJackpotSnapshot);   // current_jackpot_amount NOT NULL

        em.persist(c);
        return c;
    }

    @Test
    @DisplayName("findByBetId() returns persisted contribution with relations and snapshot amounts")
    void findByBetId_returnsContribution() {
        JackpotConfig cfg = persistConfig("fixed-fixed");
        Jackpot jp = persistJackpot(1, cfg);

        BigDecimal stake = new BigDecimal("5.00");
        BigDecimal contrib = new BigDecimal("1.00");
        BigDecimal snapshot = new BigDecimal("11500.00");   // snapshot of jackpot at contribution time

        JackpotContribution persisted = persistContribution(
                20001L, 501L, jp, 2, stake, contrib, snapshot);

        em.flush();
        em.clear();

        Optional<JackpotContribution> found = contributionRepository.findByBetId(20001L);
        assertThat(found).isPresent();

        JackpotContribution c = found.get();
        assertThat(c.getBetId()).isEqualTo(20001L);
        assertThat(c.getUserId()).isEqualTo(501L);
        assertThat(c.getJackpot()).isNotNull();
        assertThat(c.getJackpot().getJackpotId()).isEqualTo(jp.getJackpotId());
        assertThat(c.getJackpotCycle()).isEqualTo(2);
        assertThat(c.isWinningContribution()).isFalse();

        assertThat(c.getStakeAmount()).isEqualByComparingTo(stake);
        assertThat(c.getContributionAmount()).isEqualByComparingTo(contrib);
        assertThat(c.getCurrentJackpotAmount()).isEqualByComparingTo(snapshot);
    }

    @Test
    @DisplayName("findByBetId() empty for unknown bet")
    void findByBetId_empty() {
        Optional<JackpotContribution> missing = contributionRepository.findByBetId(999999L);
        assertThat(missing).isEmpty();
    }
}
