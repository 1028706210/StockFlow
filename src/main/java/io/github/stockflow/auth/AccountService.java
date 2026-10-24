package io.github.stockflow.auth;

import io.github.stockflow.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;

@Service
public class AccountService implements UserDetailsService {
    private final AccountMapper accounts;
    private final PasswordEncoder passwords;
    public AccountService(AccountMapper accounts,PasswordEncoder passwords){this.accounts=accounts;this.passwords=passwords;}
    @Override public UserDetails loadUserByUsername(String username){
        var account=accounts.byUsername(username);
        if(account==null)throw new UsernameNotFoundException("Account not found");
        return new AccountPrincipal(account);
    }
    public AccountPrincipal register(String username,String password){
        if(username==null||!username.matches("[a-z][a-z0-9_]{2,31}")||password==null)
            throw new IllegalArgumentException("Invalid account fields");
        int length=password.codePointCount(0,password.length());
        if(length<8||length>64||password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Invalid password length");
        var hash=passwords.encode(password);
        try{accounts.register(username,hash);}
        catch(DuplicateKeyException e){throw ApiException.conflict("USERNAME_TAKEN","Username is already registered");}
        return new AccountPrincipal(accounts.byUsername(username));
    }
}
