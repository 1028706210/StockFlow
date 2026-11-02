package io.github.stockflow.auth;

import io.github.stockflow.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.annotation.*;
import io.github.stockflow.operations.AuditService;
import io.github.stockflow.workbench.WorkbenchModels.Page;
import java.util.Map;

@Service
public class AccountService implements UserDetailsService {
    private final AccountMapper accounts;
    private final PasswordEncoder passwords;private final AuditService audit;
    public AccountService(AccountMapper accounts,PasswordEncoder passwords,AuditService audit){this.accounts=accounts;this.passwords=passwords;this.audit=audit;}
    @Override public UserDetails loadUserByUsername(String username){
        var account=accounts.byUsername(username);
        if(account==null)throw new UsernameNotFoundException("Account not found");
        return new AccountPrincipal(account);
    }
    public AccountPrincipal register(String username,String password){
        if(username==null||!username.matches("[a-z][a-z0-9_]{2,31}")||password==null)
            throw new IllegalArgumentException("Invalid account fields");
        validatePassword(password);
        var hash=passwords.encode(password);
        try{accounts.register(username,hash);}
        catch(DuplicateKeyException e){throw ApiException.conflict("USERNAME_TAKEN","Username is already registered");}
        return new AccountPrincipal(accounts.byUsername(username));
    }
    static void validatePassword(String password){
        if(password==null||password.isBlank()||password.codePointCount(0,password.length())<8||password.codePointCount(0,password.length())>64||password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("密码长度不符合要求");
    }
    @Transactional public void changePassword(long actor,String current,String replacement,String requestId){
        validatePassword(replacement);
        var account=accounts.lock(actor);
        if(account==null||!account.enabled())throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","请重新登录");
        if(current==null||current.getBytes(StandardCharsets.UTF_8).length>72||!passwords.matches(current,account.passwordHash()))
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"CURRENT_PASSWORD_INVALID","当前密码不正确");
        if(passwords.matches(replacement,account.passwordHash()))throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"PASSWORD_REUSED","请使用不同的新密码");
        accounts.password(actor,passwords.encode(replacement));
        audit.record(actor,"ACCOUNT_PASSWORD_CHANGE","ACCOUNT",actor,account.username(),null,Map.of("sessionsRevoked",true),requestId);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<AccountMapper.PublicAccount> list(String q,int page,int size){
        String pattern="%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        long total=accounts.count(pattern),pages=Math.max(1,(total+size-1)/size);int current=(int)Math.min(page,pages);
        return new Page<>(accounts.list(pattern,size,(long)(current-1)*size),total,current,size,pages);
    }
    @Transactional public AccountMapper.PublicAccount enabled(long actor,long id,boolean enabled,String requestId){
        var account=accounts.lock(id);if(account==null)throw ApiException.notFound();
        if(!enabled&&(actor==id||account.role().equals("ADMIN")))throw ApiException.conflict("ACCOUNT_PROTECTED","不能停用本人或管理员账号");
        var before=new AccountMapper.PublicAccount(id,account.username(),account.role(),account.enabled());
        if(account.enabled()==enabled)return before;
        accounts.enabled(id,enabled);
        var after=new AccountMapper.PublicAccount(id,account.username(),account.role(),enabled);
        audit.record(actor,"ACCOUNT_STATUS","ACCOUNT",id,account.username(),before,after,requestId);return after;
    }
}
