package com.capsa.classification.internal.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "classification_memory_entries")
public class ClassificationMemoryEntryEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "normalized_content", nullable = false, columnDefinition = "TEXT")
    private String normalizedContent;

    @Column(name = "selected_list_id", nullable = false)
    private UUID selectedListId;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public ClassificationMemoryEntryEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getNormalizedContent() { return normalizedContent; }
    public void setNormalizedContent(String normalizedContent) { this.normalizedContent = normalizedContent; }

    public UUID getSelectedListId() { return selectedListId; }
    public void setSelectedListId(UUID selectedListId) { this.selectedListId = selectedListId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
