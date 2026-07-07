package com.berzantas.kanban.epic;

import com.berzantas.kanban.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "epics")
@Getter
@Setter
public class Epic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false, updatable = false)
    private Team team;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Service-owned: EpicService advances this on real changes (dirty-check). Not
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
