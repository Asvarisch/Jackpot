package com.asvarishch.jackpot.model;

import com.asvarishch.jackpot.enums.Slot;
import com.asvarishch.jackpot.enums.StrategyKey;
import com.asvarishch.jackpot.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * One strategy binding inside a JackpotConfig bundle (e.g., CONTRIBUTION/FIXED, REWARD/VARIABLE, etc.).
 * Stores JSON payload -> flexible deserialization by strategy services.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"jackpotConfig"})
@EqualsAndHashCode(of = "id", callSuper = false)
@Entity
@Table(
        name = "config_entries",
        uniqueConstraints = {
                // Prevent duplicate slot within the same bundle (1 entry per slot).
                @UniqueConstraint(name = "uk_config_slot", columnNames = {"jackpot_config_id", "slot"})
        }
)
public class ConfigEntry extends AuditableEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jackpot_config_id", referencedColumnName = "jackpot_config_id", nullable = false)
    private JackpotConfig jackpotConfig;

    /**
     * Logical slot (e.g., CONTRIBUTION, REWARD, ...).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "slot", length = 32, nullable = false)
    private Slot slot;

    /**
     * e.g., FIXED, VARIABLE, ....
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_key", length = 32, nullable = false)
    private StrategyKey strategyKey;

    @Lob
    @Column(name = "config_json", nullable = false)
    private String configJson;
}
