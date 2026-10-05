package com.capsa.runtime.security;

import com.capsa.users.api.CurrentUser;
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

    private UserId resolvedId;

    @Override
    public UserId userId() {
        if (resolvedId == null) {
            String oidcSubject = principal.getName();
            String email;
            String name;
            if (principal instanceof JsonWebToken jwt) {
                email = jwt.getClaim("email");
                if (email == null) email = oidcSubject;
                name = jwt.getClaim("name");
            } else {
                email = oidcSubject;
                name = null;
            }
            var user = userService.findOrProvision(oidcSubject, email, name);
            resolvedId = user.userId();
        }
        return resolvedId;
    }
}
