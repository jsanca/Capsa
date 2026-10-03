package com.capsa.users.internal.service;

import com.capsa.users.api.UserNotFoundException;
import com.capsa.users.api.UserId;
import com.capsa.users.api.UserService;
import com.capsa.users.api.UserView;
import com.capsa.users.internal.domain.User;
import com.capsa.users.internal.persistence.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Inject
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserView findOrProvision(final String oidcSubject,
                                    final String email,
                                    final String name) {

        return userRepository.findByOidcSubject(oidcSubject)
            .map(UserConverter::toView)
            .orElseGet(() -> {
                var user = User.create(oidcSubject, email, name);
                var entity = UserConverter.toEntity(user);
                userRepository.save(entity);
                return UserConverter.toView(entity);
            });
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public UserView findById(final UserId userId) {

        return userRepository.findById(userId.value())
            .map(UserConverter::toView)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId.value()));
    }
}
