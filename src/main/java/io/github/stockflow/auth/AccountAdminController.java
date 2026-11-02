package io.github.stockflow.auth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.github.stockflow.workbench.WorkbenchModels.Page;

@RestController @Validated @RequestMapping("/api/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AccountAdminController {
    private final AccountService accounts;
    public AccountAdminController(AccountService accounts){this.accounts=accounts;}
    public record Status(@NotNull Boolean enabled){}
    @GetMapping public Page<AccountMapper.PublicAccount> list(@RequestParam(defaultValue="") @Size(max=32)String q,@RequestParam(defaultValue="1") @Min(1) @Max(1000000)int page,@RequestParam(defaultValue="8") @Min(1) @Max(50)int size){return accounts.list(q,page,size);}
    @PutMapping("/{id}/enabled") public AccountMapper.PublicAccount enabled(Authentication auth,@PathVariable @Positive long id,@Valid @RequestBody Status input,HttpServletRequest request){return accounts.enabled(Actor.id(auth),id,input.enabled(),(String)request.getAttribute("requestId"));}
}
