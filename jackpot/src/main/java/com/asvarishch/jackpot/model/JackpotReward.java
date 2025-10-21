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
@ToString(exclude = {"jackpot"})
@EqualsAndHashCode(of = "rewardId", callSuper = false)
@Entity
@Table(
        name = "jackpot_rewards",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reward_bet_id", columnNames = {"bet_id"}),
                @UniqueConstraint(name = "uk_reward_jackpot_cycle", columnNames = {"jackpot_id", "jackpot_cycle"})
        },
        indexes = {
                @Index(name = "ix_reward_jackpot_id", columnList = "jackpot_id"),
                @Index(name = "ix_reward_cycle", columnList = "jackpot_cycle")
        }
)
public class JackpotReward extends AuditableEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reward_id")
    private Long rewardId;

    @Column(name = "bet_id", nullable = false)
    private Long betId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jackpot_id", nullable = false)
    private Jackpot jackpot;

    @Column(name = "jackpot_reward_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal jackpotRewardAmount;

    /** Cycle at which the win happened. */
    @Column(name = "jackpot_cycle", nullable = false)
    private int jackpotCycle;
}
