module capsa.users {
    exports com.capsa.users.api;

    // Hibernate ORM needs reflection access to entities in JVM mode
    opens com.capsa.users.internal.persistence.entity;
    // Quarkus ArC needs reflection access to generate CDI proxies
    opens com.capsa.users.internal.persistence.repository;
    opens com.capsa.users.internal.service;

    requires jakarta.inject;
    requires jakarta.cdi;
    requires jakarta.persistence;
    requires jakarta.transaction;
}
