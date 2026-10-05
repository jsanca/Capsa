package com.capsa.bootstrap.internal.service;

import com.capsa.bootstrap.api.BootstrapAlreadyClaimedException;
import com.capsa.bootstrap.api.BootstrapService;
import com.capsa.bootstrap.api.BootstrapStatus;
import com.capsa.bootstrap.api.BootstrapTokenInvalidException;
import com.capsa.bootstrap.api.ClaimBootstrapCommand;
import com.capsa.bootstrap.internal.persistence.repository.BootstrapRepository;
import com.capsa.users.api.ExternalIdentity;
import com.capsa.users.api.Role;
import com.capsa.users.api.UserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@ApplicationScoped
public class BootstrapServiceImpl implements BootstrapService {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapServiceImpl.class);

    @ConfigProperty(name = "capsa.bootstrap.token")
    Optional<String> configuredToken;

    private final BootstrapRepository bootstrapRepository;
    private final UserService userService;

    @Inject
    public BootstrapServiceImpl(BootstrapRepository bootstrapRepository, UserService userService) {
        this.bootstrapRepository = bootstrapRepository;
        this.userService = userService;
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public BootstrapStatus getStatus() {
        return bootstrapRepository.findBootstrapState()
            .map(e -> BootstrapStatus.claimed(e.getClaimedAt()))
            .orElseGet(BootstrapStatus::notYetClaimed);
    }

    @Override
    @Transactional
    public BootstrapStatus claim(ExternalIdentity identity, ClaimBootstrapCommand command) {
        // Validate token using constant-time comparison (never log the token value)
        String configured = configuredToken.orElse("");
        if (configured.isBlank() || !MessageDigest.isEqual(
                command.token().getBytes(StandardCharsets.UTF_8),
                configured.getBytes(StandardCharsets.UTF_8))) {
            LOG.warn("Bootstrap claim rejected: invalid token (userId will not be created)");
            throw new BootstrapTokenInvalidException();
        }

        // Provision the user (concurrency-safe, idempotent)
        var userView = userService.findOrProvision(
            identity.issuer(), identity.subject(), identity.email(), identity.displayName());

        // Atomically claim the bootstrap singleton
        boolean claimed = bootstrapRepository.claim(userView.userId().value());
        if (!claimed) {
            LOG.warn("Bootstrap claim rejected: already claimed");
            throw new BootstrapAlreadyClaimedException();
        }

        // Assign ADMIN role — within the same transaction as the claim
        userService.assignRole(userView.userId(), Role.ADMIN);

        LOG.info("Bootstrap claimed userId={}", userView.userId().value());
        return bootstrapRepository.findBootstrapState()
            .map(e -> BootstrapStatus.claimed(e.getClaimedAt()))
            .orElseThrow(() -> new IllegalStateException("Bootstrap state missing after claim"));
    }
}
