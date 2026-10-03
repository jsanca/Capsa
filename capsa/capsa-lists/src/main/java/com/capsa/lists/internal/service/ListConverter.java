package com.capsa.lists.internal.service;

import com.capsa.lists.api.ListId;
import com.capsa.lists.api.ListView;
import com.capsa.lists.internal.domain.CapsaList;
import com.capsa.lists.internal.persistence.entity.ListEntity;
import com.capsa.users.api.UserId;

final class ListConverter {
    private ListConverter() {}

    static ListView toView(CapsaList list) {
        return new ListView(list.id(), list.name(), list.purpose(), list.ownerId());
    }

    static ListView toView(ListEntity entity) {
        return new ListView(
            new ListId(entity.getId()),
            entity.getName(),
            entity.getPurpose(),
            new UserId(entity.getOwnerId())
        );
    }

    static ListEntity toEntity(CapsaList list) {
        ListEntity entity = new ListEntity();
        entity.setId(list.id().value());
        entity.setOwnerId(list.ownerId().value());
        entity.setName(list.name());
        entity.setPurpose(list.purpose());
        entity.setCreatedAt(list.createdAt());
        return entity;
    }
}
