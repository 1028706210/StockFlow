package io.github.stockflow.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.List;

public final class AccountPrincipal extends User {
    private final long id;
    public AccountPrincipal(AccountMapper.Account account){
        super(account.username(),account.passwordHash(),account.enabled(),true,true,true,
            "ADMIN".equals(account.role())
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),new SimpleGrantedAuthority("ROLE_WAREHOUSE"))
                : List.of(new SimpleGrantedAuthority("ROLE_"+account.role())));
        this.id=account.id();
    }
    public long id(){return id;}
}
