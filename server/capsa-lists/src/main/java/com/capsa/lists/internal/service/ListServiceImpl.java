package com.capsa.lists.internal.service;

import com.capsa.lists.api.CreateListCommand;
import com.capsa.lists.api.ListAccessDeniedException;
import com.capsa.lists.api.ListId;
import com.capsa.lists.api.ListNotFoundException;
import com.capsa.lists.api.ListService;
import com.capsa.lists.api.ListView;
import com.capsa.lists.internal.domain.CapsaList;
import com.capsa.lists.internal.persistence.repository.ListRepository;
import com.capsa.observability.api.EventActor;
import com.capsa.observability.api.Observability;
import com.capsa.observability.api.events.ListCreated;
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
public class ListServiceImpl implements ListService {

    private static final Logger LOG = LoggerFactory.getLogger(ListServiceImpl.class);

    private final ListRepository listRepository;
    private final Observability observability;

    @Inject
    public ListServiceImpl(ListRepository listRepository, Observability observability) {
        this.listRepository = listRepository;
        this.observability = observability;
    }

    @Override
    @Transactional
    public ListView create(final UserId ownerId, final CreateListCommand command) {
        var list = CapsaList.create(ownerId, command.name(), command.purpose());
        var entity = ListConverter.toEntity(list);
        listRepository.save(entity);
        observability.emit(new ListCreated(
            UUID.randomUUID(),
            Instant.now(),
            new EventActor.User(ownerId.value()),
            list.id().value(),
            list.name()
        ));
        LOG.debug("Persisted list listId={} ownerId={}", list.id().value(), ownerId.value());
        return ListConverter.toView(list);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public ListView getById(final UserId requestingUser, final ListId listId) {
        var entity = listRepository.findById(listId.value())
            .orElseThrow(() -> {
                LOG.debug("List lookup miss listId={} requestingUserId={}", listId.value(), requestingUser.value());
                return new ListNotFoundException("List not found: " + listId.value());
            });
        if (!entity.getOwnerId().equals(requestingUser.value())) {
            LOG.warn("List access denied listId={} requestingUserId={} ownerId={}",
                listId.value(), requestingUser.value(), entity.getOwnerId());
            throw new ListAccessDeniedException("Access denied to list: " + listId.value());
        }
        return ListConverter.toView(entity);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ListView> getByUser(final UserId ownerId) {
        var result = listRepository.findByOwnerId(ownerId.value())
            .stream()
            .map(ListConverter::toView)
            .toList();
        LOG.debug("Loaded owner lists ownerId={} count={}", ownerId.value(), result.size());
        return result;
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public void verifyContributionAccess(final UserId userId, final ListId listId) {
        var entity = listRepository.findById(listId.value())
            .orElseThrow(() -> {
                LOG.debug("List lookup miss during access check listId={} userId={}", listId.value(), userId.value());
                return new ListNotFoundException("List not found: " + listId.value());
            });
        if (!entity.getOwnerId().equals(userId.value())) {
            LOG.warn("List contribution access denied listId={} userId={} ownerId={}",
                listId.value(), userId.value(), entity.getOwnerId());
            throw new ListAccessDeniedException("Access denied to list: " + listId.value());
        }
    }
}
