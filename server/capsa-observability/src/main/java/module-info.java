module capsa.observability {
    exports com.capsa.observability.api;
    exports com.capsa.observability.api.events;

    requires jakarta.inject;
    requires jakarta.cdi;
    requires jakarta.json;
    requires jakarta.json.bind;
    requires jakarta.transaction;
    requires org.slf4j;

    opens com.capsa.observability.internal;
}
