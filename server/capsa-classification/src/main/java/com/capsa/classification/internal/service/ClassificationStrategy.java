package com.capsa.classification.internal.service;

import com.capsa.classification.api.ClassificationRequest;
import com.capsa.classification.api.ClassificationResult;

interface ClassificationStrategy {
    ClassificationResult classify(ClassificationRequest request);
}
