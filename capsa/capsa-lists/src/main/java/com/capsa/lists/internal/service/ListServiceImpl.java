package com.capsa.lists.internal.service;

import com.capsa.lists.api.CreateListCommand;
import com.capsa.lists.api.ListAccessDeniedException;
import com.capsa.lists.api.ListId;
import com.capsa.lists.api.ListNotFoundException;
import com.capsa.lists.api.ListService;
import com.capsa.lists.api.ListView;
import com.capsa.lists.internal.domain.CapsaList;
import com.capsa.lists.internal.persistence.repository.ListRepository;
import com.capsa.users.api.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class ListServiceImpl implements ListService {

    private final ListRepository listRepository;

    @Inject
    public ListServiceImpl(ListRepository listRepository) {
        this.listRepository = listRepository;
    }

    @Override
    @Transactional
    public ListView create(final UserId ownerId, final CreateListCommand command) {
        var list = CapsaList.create(ownerId, command.name(), command.purpose());
        var entity = ListConverter.toEntity(list);
        listRepository.save(entity);
        return ListConverter.toView(list);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public ListView getById(final UserId requestingUser, final ListId listId) {
        var entity = listRepository.findById(listId.value())
            .orElseThrow(() -> new ListNotFoundException("List not found: " + listId.value()));
        if (!entity.getOwnerId().equals(requestingUser.value())) {
            throw new ListAccessDeniedException("Access denied to list: " + listId.value());
        }
        return ListConverter.toView(entity);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ListView> getByUser(final UserId ownerId) {
        return listRepository.findByOwnerId(ownerId.value())
            .stream()
            .map(ListConverter::toView)
            .toList();
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public void verifyContributionAccess(final UserId userId, final ListId listId) {
        var entity = listRepository.findById(listId.value())
            .orElseThrow(() -> new ListNotFoundException("List not found: " + listId.value()));
        if (!entity.getOwnerId().equals(userId.value())) {
            throw new ListAccessDeniedException("Access denied to list: " + listId.value());
        }
    }
}
