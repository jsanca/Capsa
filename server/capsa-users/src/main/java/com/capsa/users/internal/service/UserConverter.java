package com.capsa.users.internal.service;

import com.capsa.users.api.UserId;
import com.capsa.users.api.UserView;
import com.capsa.users.internal.domain.User;
import com.capsa.users.internal.persistence.entity.UserEntity;

final class UserConverter {
    private UserConverter() {}

    static UserView toView(User user) {
        return new UserView(new UserId(user.id()), user.email(), user.name());
    }

    static UserView toView(UserEntity entity) {
        return new UserView(new UserId(entity.getId()), entity.getEmail(), entity.getName());
    }

    static UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.id());
        entity.setOidcSubject(user.oidcSubject());
        entity.setEmail(user.email());
        entity.setName(user.name());
        entity.setCreatedAt(user.createdAt());
        return entity;
    }
}
