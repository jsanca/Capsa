module capsa.lists {
    exports com.capsa.lists.api;

    // Hibernate ORM needs reflection access to entities in JVM mode
    opens com.capsa.lists.internal.persistence.entity;
    // Quarkus ArC needs reflection access to generate CDI proxies
    opens com.capsa.lists.internal.persistence.repository;
    opens com.capsa.lists.internal.service;
    // Quarkus REST and ArC need access to the resource class
    opens com.capsa.lists.internal.rest;

    requires capsa.users;
    requires capsa.observability;
    requires jakarta.inject;
    requires jakarta.cdi;
    requires jakarta.persistence;
    requires jakarta.transaction;
    requires jakarta.ws.rs;
    requires org.slf4j;
}
