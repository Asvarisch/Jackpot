package com.asvarishch.jackpot.model;

import com.asvarishch.jackpot.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "jackpot")
@EqualsAndHashCode(of = "contributionId", callSuper = false)
@Entity
@Table(
        name = "jackpot_contributions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_contrib_bet_id", columnNames = {"bet_id"})
        },
        indexes = {
                @Index(name = "ix_contrib_jackpot_id", columnList = "jackpot_id"),
                @Index(name = "ix_contrib_cycle", columnList = "jackpot_cycle")
        }
)
public class JackpotContribution extends AuditableEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contribution_id")
    private Long contributionId;

    @Column(name = "bet_id", nullable = false, unique = true)
    private Long betId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jackpot_id", nullable = false)
    private Jackpot jackpot;

    @Column(name = "stake_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal stakeAmount;

    @Column(name = "contribution_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal contributionAmount;

    @Column(name = "current_jackpot_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal currentJackpotAmount;

    @Column(name = "jackpot_cycle", nullable = false)
    private int jackpotCycle;

    @Column(name = "is_winning_contribution", nullable = false)
    private boolean winningContribution = false;

    @Column(name = "is_evaluated", nullable = false)
    private boolean evaluated = false;
}
