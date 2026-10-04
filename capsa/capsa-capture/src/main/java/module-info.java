module capsa.capture {
    exports com.capsa.capture.api;

    opens com.capsa.capture.internal.persistence.entity;
    opens com.capsa.capture.internal.persistence.repository;
    opens com.capsa.capture.internal.service;
    opens com.capsa.capture.internal.rest;

    requires capsa.users;
    requires capsa.lists;
    requires capsa.items;
    requires capsa.classification;
    requires capsa.observability;
    requires jakarta.inject;
    requires jakarta.cdi;
    requires jakarta.persistence;
    requires jakarta.transaction;
    requires jakarta.ws.rs;
    requires org.slf4j;
}
