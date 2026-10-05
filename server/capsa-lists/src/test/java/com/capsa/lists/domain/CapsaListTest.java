package com.capsa.lists.domain;

import com.capsa.lists.internal.domain.CapsaList;
import com.capsa.users.api.UserId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CapsaListTest {

    private static final UserId OWNER = new UserId(UUID.randomUUID());

    @Test
    void createListSetsAllFields() {
        var list = CapsaList.create(OWNER, "Work Tasks", "Work-related items");
        assertNotNull(list.id());
        assertEquals(OWNER, list.ownerId());
        assertEquals("Work Tasks", list.name());
        assertEquals("Work-related items", list.purpose());
        assertNotNull(list.createdAt());
    }

    @Test
    void createListAllowsNullPurpose() {
        var list = CapsaList.create(OWNER, "Inbox", null);
        assertNull(list.purpose());
    }

    @Test
    void createListStripsWhitespaceFromName() {
        var list = CapsaList.create(OWNER, "  Work Tasks  ", null);
        assertEquals("Work Tasks", list.name());
    }

    @Test
    void createListRejectsNullOwner() {
        assertThrows(NullPointerException.class, () -> CapsaList.create(null, "Work", null));
    }

    @Test
    void createListRejectsNullName() {
        assertThrows(NullPointerException.class, () -> CapsaList.create(OWNER, null, null));
    }

    @Test
    void createListRejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> CapsaList.create(OWNER, "   ", null));
    }

    @Test
    void twoListsWithSameInputHaveDifferentIds() {
        var a = CapsaList.create(OWNER, "Work", null);
        var b = CapsaList.create(OWNER, "Work", null);
        assertNotEquals(a.id(), b.id());
    }
}
