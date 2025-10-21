package com.asvarishch.jackpot.repository;

import com.asvarishch.jackpot.model.Jackpot;
import com.asvarishch.jackpot.model.JackpotConfig;
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
        "spring.sql.init.mode=never",
})
class JackpotRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JackpotRepository jackpotRepository;

    // ---------- helpers ----------

    /**
     * Persists a JackpotConfig with a specific id and name.
     * We use explicit IDs here since JackpotConfig id is a natural key in your model.
     */
    private JackpotConfig persistConfig(String id, String name) {
        JackpotConfig cfg = new JackpotConfig();
        cfg.setJackpotConfigId(id);
        cfg.setName(name);
        em.persist(cfg);
        return cfg;
    }

    /**
     * Persists a Jackpot linked to the given config.
     */
    private Jackpot persistJackpot(String name, BigDecimal initial, BigDecimal current, int cycle, JackpotConfig cfg) {
        Jackpot j = new Jackpot();
        j.setName(name);
        j.setInitialAmount(initial);
        j.setCurrentAmount(current);
        j.setCycle(cycle);
        j.setJackpotConfig(cfg);
        em.persist(j);
        return j;
    }

    // ---------- tests ----------

    @Test
    @DisplayName("findByJackpotIdWithConfig() returns jackpot with its config eagerly loaded")
    void findByJackpotIdWithConfig_returnsWithConfig() {
        // Arrange: seed config + jackpot
        JackpotConfig cfg = persistConfig("fixed-fixed", "Fixed contribution / Fixed reward chance");
        Jackpot j = persistJackpot("Jackpot FIXED/FIXED",
                new BigDecimal("10000.00"), new BigDecimal("10000.00"), 0, cfg);
        em.flush();
        em.clear();

        // Act
        Optional<Jackpot> found = jackpotRepository.findByJackpotIdWithConfig(j.getJackpotId());

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Jackpot FIXED/FIXED");
        assertThat(found.get().getJackpotConfig()).isNotNull();
        assertThat(found.get().getJackpotConfig().getJackpotConfigId()).isEqualTo("fixed-fixed");
    }

    @Test
    @DisplayName("findByIdForUpdate() returns row for pessimistic write section (basic presence check)")
    void findByIdForUpdate_returnsRow() {
        // Arrange
        JackpotConfig cfg = persistConfig("fixed-fixed-2", "Another fixed");
        Jackpot j = persistJackpot("JP",
                new BigDecimal("10000.00"), new BigDecimal("12500.00"), 3, cfg);
        em.flush();
        em.clear();

        // Act
        Optional<Jackpot> locked = jackpotRepository.findByIdForUpdate(j.getJackpotId());

        // Assert
        assertThat(locked).isPresent();
        assertThat(locked.get().getJackpotId()).isEqualTo(j.getJackpotId());
        // NOTE: We don't assert actual blocking semantics here; that's for service IT with multi-tx/race.
    }

    @Test
    @DisplayName("findByJackpotIdWithConfig() returns empty for unknown id")
    void findByJackpotIdWithConfig_unknown() {
        // Act
        Optional<Jackpot> notFound = jackpotRepository.findByJackpotIdWithConfig(9_999_999L);

        // Assert
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("Persist and modify: currentAmount and cycle update is visible on flush")
    void updateJackpotFields_flush() {
        // Arrange
        JackpotConfig cfg = persistConfig("variable-fixed", "Variable contribution / Fixed chance");
        Jackpot j = persistJackpot("JP2",
                new BigDecimal("10000.00"), new BigDecimal("14500.00"), 5, cfg);

        // Act: simulate reset/next cycle
        j.setCurrentAmount(new BigDecimal("10000.00"));
        j.setCycle(6);
        em.flush();
        em.clear();

        // Assert
        Optional<Jackpot> reloaded = jackpotRepository.findByJackpotIdWithConfig(j.getJackpotId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getCurrentAmount()).isEqualByComparingTo("10000.00");
        assertThat(reloaded.get().getCycle()).isEqualTo(6);
    }
}
