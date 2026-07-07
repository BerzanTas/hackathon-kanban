package com.berzantas.kanban.ticket;

import com.berzantas.kanban.epic.Epic;
import com.berzantas.kanban.team.Team;
import com.berzantas.kanban.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "tickets")
@Getter
@Setter
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "epic_id")
    private Epic epic;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private TicketType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private TicketState state;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Service-owned: TicketService advances this on real changes (dirty-check). Not
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
