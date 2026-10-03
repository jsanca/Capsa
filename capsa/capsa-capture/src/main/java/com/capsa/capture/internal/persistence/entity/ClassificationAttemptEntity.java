package com.capsa.capture.internal.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "classification_attempts")
public class ClassificationAttemptEntity {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "capture_id", nullable = false)
    public UUID captureId;

    @Column(name = "outcome")
    public String outcome;

    @Column(name = "candidates")
    public String candidates;

    @Column(name = "attempted_at", nullable = false)
    public Instant attemptedAt;
}
