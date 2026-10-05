module capsa.bootstrap {
    exports com.capsa.bootstrap.api;

    // Hibernate ORM needs reflection access to entities in JVM mode
    opens com.capsa.bootstrap.internal.persistence.entity;
    // Quarkus ArC needs reflection access to generate CDI proxies
    opens com.capsa.bootstrap.internal.persistence.repository;
    opens com.capsa.bootstrap.internal.service;
    // Quarkus REST and ArC need access to the resource class
    opens com.capsa.bootstrap.internal.rest;

    requires capsa.users;
    requires jakarta.inject;
    requires jakarta.cdi;
    requires jakarta.persistence;
    requires jakarta.transaction;
    requires jakarta.ws.rs;
    requires org.eclipse.microprofile.config;
    requires org.slf4j;
}
