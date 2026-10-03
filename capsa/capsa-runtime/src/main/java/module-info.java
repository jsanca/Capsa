open module capsa.runtime {
    requires capsa.users;
    requires capsa.lists;
    requires capsa.items;
    requires capsa.capture;
    requires capsa.classification;

    // Jakarta EE APIs used directly in runtime wiring code
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.ws.rs;

    // MicroProfile JWT for claim extraction in OidcCurrentUser
    requires microprofile.jwt.auth.api;
}
