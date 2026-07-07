package com.berzantas.kanban.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "teams")
@Getter
@Setter
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Service-owned: TeamService advances this on rename (dirty-check). Not
    // @CreationTimestamp, which would make the column insert-only and drop service updates.
    @Column(name = "modified_at", nullable = false)
    private OffsetDateTime modifiedAt;

    @PrePersist
    void initModifiedAt() {
        if (modifiedAt == null) {
            modifiedAt = OffsetDateTime.now();
        }
    }
}
