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
import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.Observability;
import com.capsa.observability.api.events.ItemCompleted;
import com.capsa.observability.api.events.ItemCreated;
import com.capsa.users.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ItemServiceImpl implements ItemService {

    private static final Logger LOG = LoggerFactory.getLogger(ItemServiceImpl.class);

    private final ItemRepository itemRepository;
    private final ListService listService;
    private final Observability observability;

    @Inject
    public ItemServiceImpl(ItemRepository itemRepository, ListService listService, Observability observability) {
        this.itemRepository = itemRepository;
        this.listService = listService;
        this.observability = observability;
    }

    @Override
    @Transactional
    public ItemView create(UserId userId, CreateItemCommand command) {
        var listId = new ListId(command.listId());
        listService.verifyContributionAccess(userId, listId);
        var item = Item.create(listId, command.name(), command.notes());
        var saved = ItemConverter.toEntity(item);
        itemRepository.save(saved);
        observability.emit(new ItemCreated(
            UUID.randomUUID(),
            Instant.now(),
            new EventActor.User(userId.value()),
            saved.getId(),
            saved.getListId(),
            null
        ));
        LOG.debug("Persisted item itemId={} listId={} userId={}", saved.getId(), saved.getListId(), userId.value());
        return ItemConverter.toView(saved);
    }

    @Override
    @Transactional
    public ItemView createFromCapture(UserId userId, ListId listId, UUID captureId, String name, String notes) {
        listService.verifyContributionAccess(userId, listId);
        var item = Item.createFromCapture(listId, captureId, name, notes);
        var saved = ItemConverter.toEntity(item);
        itemRepository.save(saved);
        observability.emit(new ItemCreated(
            UUID.randomUUID(),
            Instant.now(),
            new EventActor.User(userId.value()),
            saved.getId(),
            saved.getListId(),
            captureId
        ));
        LOG.debug("Persisted item from capture itemId={} listId={} captureId={} userId={}",
            saved.getId(), saved.getListId(), captureId, userId.value());
        return ItemConverter.toView(saved);
    }

    @Override
    @Transactional
    public ItemView complete(UserId userId, ItemId itemId) {
        var entity = itemRepository.findById(itemId.value())
            .orElseThrow(() -> {
                LOG.debug("Item lookup miss itemId={} userId={}", itemId.value(), userId.value());
                return new ItemNotFoundException("Item not found: " + itemId.value());
            });
        listService.verifyContributionAccess(userId, new ListId(entity.getListId()));
        if ("DONE".equals(entity.getStatus())) {
            LOG.debug("Item already DONE; idempotent return itemId={} userId={}", entity.getId(), userId.value());
            return ItemConverter.toView(entity);
        }
        entity.setStatus("DONE");
        entity.setCompletedAt(Instant.now());
        observability.emit(new ItemCompleted(
            UUID.randomUUID(),
            Instant.now(),
            new EventActor.User(userId.value()),
            entity.getId()
        ));
        LOG.info("Completed item itemId={} listId={} userId={}", entity.getId(), entity.getListId(), userId.value());
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
        var views = entities.stream().map(ItemConverter::toView).toList();
        LOG.debug("Loaded items listId={} filter={} count={} userId={}",
            listId.value(), filter, views.size(), userId.value());
        return views;
    }
}
