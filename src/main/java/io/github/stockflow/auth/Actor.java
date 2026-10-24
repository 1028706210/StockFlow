package io.github.stockflow.auth;
import org.springframework.security.core.Authentication;
public final class Actor {
    private Actor(){}
    public static long id(Authentication authentication){
        if(authentication.getPrincipal() instanceof AccountPrincipal account)return account.id();
        throw new IllegalStateException("Persisted account principal is required");
    }
}
