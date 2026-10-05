package com.capsa.capture.internal.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "captures")
public class CaptureEntity {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "original_content", nullable = false)
    public String originalContent;

    @Column(name = "normalized_content", nullable = false)
    public String normalizedContent;

    @Column(name = "processing_status", nullable = false)
    public String processingStatus;

    @Column(name = "captured_at", nullable = false)
    public Instant capturedAt;
}
