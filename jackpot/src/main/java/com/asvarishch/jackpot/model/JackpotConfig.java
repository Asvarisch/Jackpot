package com.asvarishch.jackpot.model;

import com.asvarishch.jackpot.model.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"entries", "jackpots"})
@EqualsAndHashCode(of = "jackpotConfigId", callSuper = false)
@Entity
@Table(name = "jackpot_configs")
public class JackpotConfig extends AuditableEntity<String> {

    /** e.g., "fixed-fixed". */
    @Id
    @Column(name = "jackpot_config_id", length = 128, nullable = false)
    private String jackpotConfigId;

    @Column(name = "name", length = 256)
    private String name;

    @OneToMany(mappedBy = "jackpotConfig", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ConfigEntry> entries = new ArrayList<>();

    @OneToMany(mappedBy = "jackpotConfig", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Jackpot> jackpots = new ArrayList<>();
}
