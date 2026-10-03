package com.capsa.items.internal.service;

import com.capsa.items.api.CreateItemCommand;
import com.capsa.items.api.ItemId;
import com.capsa.items.api.ItemNotFoundException;
import com.capsa.items.api.ItemService;
import com.capsa.items.api.ItemStatusFilter;
import com.capsa.items.api.ItemView;
import com.capsa.items.internal.domain.Item;
import com.capsa.items.internal.persistence.repository.ItemRepository;
import com.capsa.lists.api.ListId;
import com.capsa.lists.api.ListService;
import com.capsa.users.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;
    private final ListService listService;

    @Inject
    public ItemServiceImpl(ItemRepository itemRepository, ListService listService) {
        this.itemRepository = itemRepository;
        this.listService = listService;
    }

    @Override
    @Transactional
    public ItemView create(UserId userId, CreateItemCommand command) {
        var listId = new ListId(command.listId());
        listService.verifyContributionAccess(userId, listId);
        var item = Item.create(listId, command.name(), command.notes());
        itemRepository.save(ItemConverter.toEntity(item));
        return ItemConverter.toView(ItemConverter.toEntity(item));
    }

    @Override
    @Transactional
    public ItemView createFromCapture(UserId userId, ListId listId, UUID captureId, String name, String notes) {
        listService.verifyContributionAccess(userId, listId);
        var item = Item.createFromCapture(listId, captureId, name, notes);
        itemRepository.save(ItemConverter.toEntity(item));
        return ItemConverter.toView(ItemConverter.toEntity(item));
    }

    @Override
    @Transactional
    public ItemView complete(UserId userId, ItemId itemId) {
        var entity = itemRepository.findById(itemId.value())
            .orElseThrow(() -> new ItemNotFoundException("Item not found: " + itemId.value()));
        listService.verifyContributionAccess(userId, new ListId(entity.getListId()));
        if ("DONE".equals(entity.getStatus())) {
            return ItemConverter.toView(entity);
        }
        entity.setStatus("DONE");
        entity.setCompletedAt(Instant.now());
        return ItemConverter.toView(entity);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ItemView> getByList(UserId userId, ListId listId, ItemStatusFilter filter) {
        listService.verifyContributionAccess(userId, listId);
        var entities = switch (filter) {
            case ACTIVE  -> itemRepository.findByListIdAndStatus(listId.value(), "PENDING");
            case HISTORY -> itemRepository.findByListIdAndStatus(listId.value(), "DONE");
            case ALL     -> itemRepository.findByListId(listId.value());
        };
        return entities.stream().map(ItemConverter::toView).toList();
    }
}
