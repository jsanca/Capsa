open module capsa.runtime {
    requires capsa.users;
    requires capsa.lists;
    requires capsa.items;
    requires capsa.capture;
    requires capsa.classification;
    requires capsa.observability;

    // Jakarta EE APIs used directly in runtime wiring code
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.ws.rs;

    // MicroProfile JWT for claim extraction in OidcCurrentUser
    requires microprofile.jwt.auth.api;

    // Application logging via SLF4J; provider comes from quarkus-logging
    requires org.slf4j;
}
