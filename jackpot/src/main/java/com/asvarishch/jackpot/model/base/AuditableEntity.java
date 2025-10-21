package com.asvarishch.jackpot.model.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;


@Getter
@Setter
@MappedSuperclass
public abstract class AuditableEntity<T extends Serializable> {

    @CreationTimestamp                       // Hibernate fills on INSERT it performs
    @Column(name = "created_at",             // never updated after insert
            nullable = false,
            updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")      // DB default for raw SQL (e.g., data.sql)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            setCreatedAt(Instant.now());
        }
    }
}
