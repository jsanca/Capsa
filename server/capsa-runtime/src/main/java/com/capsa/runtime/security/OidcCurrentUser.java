package com.capsa.runtime.security;

import com.capsa.users.api.CurrentUser;
import com.capsa.users.api.ExternalIdentity;
import com.capsa.users.api.UserId;
import com.capsa.users.api.UserService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import java.security.Principal;

@RequestScoped
public class OidcCurrentUser implements CurrentUser {

    @Inject
    Principal principal;

    @Inject
    UserService userService;

    private ExternalIdentity resolvedIdentity;
    private UserId resolvedId;

    @Override
    public ExternalIdentity identity() {
        if (resolvedIdentity == null) {
            resolvedIdentity = extractIdentity();
        }
        return resolvedIdentity;
    }

    @Override
    public UserId userId() {
        if (resolvedId == null) {
            var id = identity();
            var user = userService.findOrProvision(id.issuer(), id.subject(), id.email(), id.displayName());
            resolvedId = user.userId();
        }
        return resolvedId;
    }

    private ExternalIdentity extractIdentity() {
        String subject = principal.getName();
        String issuer;
        String email;
        String displayName;
        if (principal instanceof JsonWebToken jwt) {
            issuer = jwt.getIssuer();
            if (issuer == null || issuer.isBlank()) issuer = "unknown";
            email = jwt.getClaim("email");
            if (email == null || email.isBlank()) email = subject;
            displayName = jwt.getClaim("name");
        } else {
            issuer = "unknown";
            email = subject;
            displayName = null;
        }
        return new ExternalIdentity(issuer, subject, email, displayName);
    }
}
