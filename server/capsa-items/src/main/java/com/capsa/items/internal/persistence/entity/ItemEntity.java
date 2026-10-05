package com.capsa.items.internal.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "items")
public class ItemEntity {

    @Id
    private UUID id;

    @Column(name = "list_id", nullable = false)
    private UUID listId;

    @Column(name = "capture_id")
    private UUID captureId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public ItemEntity() {}

    public UUID getId()                  { return id; }
    public void setId(UUID id)           { this.id = id; }

    public UUID getListId()              { return listId; }
    public void setListId(UUID listId)   { this.listId = listId; }

    public UUID getCaptureId()           { return captureId; }
    public void setCaptureId(UUID v)     { this.captureId = v; }

    public String getName()              { return name; }
    public void setName(String name)     { this.name = name; }

    public String getNotes()             { return notes; }
    public void setNotes(String notes)   { this.notes = notes; }

    public String getStatus()            { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt()        { return createdAt; }
    public void setCreatedAt(Instant v)  { this.createdAt = v; }

    public Instant getCompletedAt()      { return completedAt; }
    public void setCompletedAt(Instant v){ this.completedAt = v; }
}
