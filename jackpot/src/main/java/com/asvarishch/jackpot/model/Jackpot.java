package com.asvarishch.jackpot.model;

import com.asvarishch.jackpot.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"jackpotConfig", "contributions", "rewards"})
@EqualsAndHashCode(of = "jackpotId", callSuper = false)
@Entity
@Table(name = "jackpots")
public class Jackpot extends AuditableEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "jackpot_id", length = 64, nullable = false)
    private Long jackpotId;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "initial_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal initialAmount;

    @Column(name = "current_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal currentAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jackpot_config_id", referencedColumnName = "jackpot_config_id", nullable = false)
    private JackpotConfig jackpotConfig;

    @OneToMany(mappedBy = "jackpot", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JackpotContribution> contributions = new ArrayList<>();

    @OneToMany(mappedBy = "jackpot", fetch = FetchType.LAZY, orphanRemoval = false)
    private List<JackpotReward> rewards = new ArrayList<>();

    /**
     * Cycle number; increments each time the jackpot is reset due to a win.
     */
    @Column(name = "cycle", nullable = false)
    private int cycle;

    /**
     * Optimistic lock for frequent updates on contributions.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public void addContribution(JackpotContribution c) {
        if (c != null) {
            c.setJackpot(this);
            contributions.add(c);
        }
    }

    public void addReward(JackpotReward r) {
        if (r != null) {
            r.setJackpot(this);
            rewards.add(r);
        }
    }

    public void resetToInitial() {
        this.currentAmount = this.initialAmount;
        this.cycle = this.cycle + 1;
    }


}
