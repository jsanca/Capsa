module capsa.items {
    exports com.capsa.items.api;

    opens com.capsa.items.internal.persistence.entity;
    opens com.capsa.items.internal.persistence.repository;
    opens com.capsa.items.internal.service;
    opens com.capsa.items.internal.rest;

    requires capsa.users;
    requires capsa.lists;
    requires capsa.observability;
    requires jakarta.inject;
    requires jakarta.cdi;
    requires jakarta.persistence;
    requires jakarta.transaction;
    requires jakarta.ws.rs;
    requires org.slf4j;
}
