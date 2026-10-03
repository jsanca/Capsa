package com.capsa.capture.internal.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "classification_resolutions")
public class ClassificationResolutionEntity {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "capture_id", nullable = false, unique = true)
    public UUID captureId;

    @Column(name = "selected_list_id", nullable = false)
    public UUID selectedListId;

    @Column(name = "resolved_by", nullable = false)
    public String resolvedBy;

    @Column(name = "resolved_at", nullable = false)
    public Instant resolvedAt;
}
