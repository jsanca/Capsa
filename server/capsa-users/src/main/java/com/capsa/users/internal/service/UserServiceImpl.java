package com.capsa.users.internal.service;

import com.capsa.users.api.Role;
import com.capsa.users.api.UserNotFoundException;
import com.capsa.users.api.UserId;
import com.capsa.users.api.UserService;
import com.capsa.users.api.UserView;
import com.capsa.users.internal.persistence.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class UserServiceImpl implements UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;

    @Inject
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserView findOrProvision(String issuer, String subject, String email, String name) {
        // Fast path: user already exists
        var existing = userRepository.findByIssuerAndSubject(issuer, subject);
        if (existing.isPresent()) {
            return UserConverter.toView(existing.get());
        }

        // Attempt atomic insert; ON CONFLICT DO NOTHING handles concurrent requests
        UUID newId = UUID.randomUUID();
        boolean inserted = userRepository.insertIfAbsent(
            newId, issuer, subject, email, name, Role.USER.name(), Instant.now());

        if (inserted) {
            LOG.info("Provisioned new user userId={}", newId);
        } else {
            LOG.debug("Concurrent provision detected; re-querying userId for issuer+subject");
        }

        // Whether we inserted or lost the race, the row now exists — re-fetch to get a managed entity
        return userRepository.findByIssuerAndSubject(issuer, subject)
            .map(UserConverter::toView)
            .orElseThrow(() -> new IllegalStateException("User missing after insert: " + subject));
    }

    @Override
    @Transactional
    public void assignRole(UserId userId, Role role) {
        var entity = userRepository.findById(userId.value())
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId.value()));
        entity.setRole(role);
        LOG.info("assignRole userId={} role={}", userId.value(), role);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public UserView findById(UserId userId) {
        return userRepository.findById(userId.value())
            .map(UserConverter::toView)
            .orElseThrow(() -> {
                LOG.debug("User lookup miss userId={}", userId.value());
                return new UserNotFoundException("User not found: " + userId.value());
            });
    }
}
