package com.capsa.items.internal.service;

import com.capsa.items.api.ItemId;
import com.capsa.items.api.ItemView;
import com.capsa.items.internal.domain.Item;
import com.capsa.items.internal.persistence.entity.ItemEntity;

class ItemConverter {

    static ItemEntity toEntity(final Item item) {
        var entity = new ItemEntity();
        entity.setId(item.getId());
        entity.setListId(item.getListId());
        entity.setCaptureId(item.getCaptureId());
        entity.setName(item.getName());
        entity.setNotes(item.getNotes());
        entity.setStatus(item.getStatus().name());
        entity.setCreatedAt(item.getCreatedAt());
        entity.setCompletedAt(item.getCompletedAt());
        return entity;
    }

    static ItemView toView(final ItemEntity entity) {
        return new ItemView(
            new ItemId(entity.getId()),
            entity.getListId(),
            entity.getCaptureId(),
            entity.getName(),
            entity.getNotes(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getCompletedAt()
        );
    }
}
