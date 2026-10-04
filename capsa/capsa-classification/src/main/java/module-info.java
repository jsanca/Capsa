module capsa.classification {
    exports com.capsa.classification.api;

    opens com.capsa.classification.internal.persistence.entity;
    opens com.capsa.classification.internal.persistence.repository;
    opens com.capsa.classification.internal.service;

    requires capsa.users;
    requires capsa.observability;
    requires jakarta.inject;
    requires jakarta.cdi;
    requires jakarta.persistence;
    requires jakarta.transaction;
    requires org.slf4j;
}
