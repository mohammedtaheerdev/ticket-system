package com.ticket.system.model;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tickets")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private AppUser requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    private AppUser assignedAgent;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private Instant closedAt;

    protected Ticket() {
    }

    public Ticket(Organization organization, String subject, String description,
                  TicketPriority priority, AppUser requester, AppUser assignedAgent) {
        this.organization = organization;
        this.subject = subject;
        this.description = description;
        this.priority = priority;
        this.requester = requester;
        this.assignedAgent = assignedAgent;
        this.status = TicketStatus.OPEN;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void update(String subject, String description, TicketPriority priority) {
        this.subject = subject;
        this.description = description;
        this.priority = priority;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
        this.closedAt = status == TicketStatus.CLOSED ? Instant.now() : null;
    }

    public void setAssignedAgent(AppUser assignedAgent) {
        this.assignedAgent = assignedAgent;
    }

    public Long getId() { return id; }
    public Organization getOrganization() { return organization; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public TicketPriority getPriority() { return priority; }
    public TicketStatus getStatus() { return status; }
    public AppUser getRequester() { return requester; }
    public AppUser getAssignedAgent() { return assignedAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getClosedAt() { return closedAt; }
}
