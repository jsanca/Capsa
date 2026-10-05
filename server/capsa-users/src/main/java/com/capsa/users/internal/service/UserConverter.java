package com.capsa.users.internal.service;

import com.capsa.users.api.UserId;
import com.capsa.users.api.UserView;
import com.capsa.users.internal.persistence.entity.UserEntity;

final class UserConverter {
    private UserConverter() {}

    static UserView toView(UserEntity entity) {
        return new UserView(
            new UserId(entity.getId()),
            entity.getEmail(),
            entity.getName(),
            entity.getRole()
        );
    }
}
