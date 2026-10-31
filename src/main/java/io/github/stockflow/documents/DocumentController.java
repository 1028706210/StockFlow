package io.github.stockflow.documents;

import io.github.stockflow.auth.Actor;
import io.github.stockflow.workbench.WorkbenchModels.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static io.github.stockflow.documents.DocumentModels.*;

@RestController @Validated @RequestMapping("/api")
public class DocumentController {
    private final DocumentService service;
    public DocumentController(DocumentService service){this.service=service;}
    @GetMapping("/receipts") @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
    public Page<Receipt> receipts(@RequestParam(defaultValue="") @Size(max=120)String q,@RequestParam(defaultValue="all") @Pattern(regexp="all|DRAFT|CONFIRMED|CANCELLED")String status,@RequestParam(defaultValue="1") @Min(1) @Max(1000000)int page,@RequestParam(defaultValue="8") @Min(1) @Max(100)int size){return service.receipts(q,status,page,size);}
    @GetMapping("/receipts/{id}") @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
    public Receipt receipt(@PathVariable @Positive long id){return service.receipt(id);}
    @PostMapping(value="/receipts",consumes=MediaType.APPLICATION_JSON_VALUE) @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
    public ResponseEntity<Receipt> createReceipt(Authentication auth,@Valid @RequestBody CreateReceipt request,HttpServletRequest http){var created=service.createReceipt(Actor.id(auth),request,requestId(http));return ResponseEntity.status(created.replayed()?200:201).body(created.document());}
    @PostMapping("/receipts/{id}/confirm") @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
    public Receipt confirmReceipt(Authentication auth,@PathVariable @Positive long id,HttpServletRequest http){return service.confirmReceipt(Actor.id(auth),id,requestId(http));}
    @PostMapping("/receipts/{id}/cancel") @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
    public Receipt cancelReceipt(Authentication auth,@PathVariable @Positive long id,HttpServletRequest http){return service.cancelReceipt(Actor.id(auth),id,requestId(http));}
    @GetMapping("/returns") @PreAuthorize("hasAnyRole('BUYER','WAREHOUSE','ADMIN')")
    public Page<Return> returns(Authentication auth,@RequestParam(defaultValue="") @Size(max=120)String q,@RequestParam(defaultValue="all") @Pattern(regexp="all|REQUESTED|CONFIRMED|CANCELLED")String status,@RequestParam(defaultValue="1") @Min(1) @Max(1000000)int page,@RequestParam(defaultValue="8") @Min(1) @Max(100)int size){return service.returns(buyerScope(auth),q,status,page,size);}
    @GetMapping("/returns/{id}") @PreAuthorize("hasAnyRole('BUYER','WAREHOUSE','ADMIN')")
    public Return returned(Authentication auth,@PathVariable @Positive long id){return service.returned(buyerScope(auth),id);}
    @PostMapping(value="/returns",consumes=MediaType.APPLICATION_JSON_VALUE) @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<Return> createReturn(Authentication auth,@Valid @RequestBody CreateReturn request,HttpServletRequest http){var created=service.createReturn(Actor.id(auth),request,requestId(http));return ResponseEntity.status(created.replayed()?200:201).body(created.document());}
    @PostMapping("/returns/{id}/confirm") @PreAuthorize("hasAnyRole('WAREHOUSE','ADMIN')")
    public Return confirmReturn(Authentication auth,@PathVariable @Positive long id,HttpServletRequest http){return service.confirmReturn(Actor.id(auth),id,requestId(http));}
    @PostMapping("/returns/{id}/cancel") @PreAuthorize("hasRole('BUYER')")
    public Return cancelReturn(Authentication auth,@PathVariable @Positive long id,HttpServletRequest http){return service.cancelReturn(Actor.id(auth),id,requestId(http));}
    private static Long buyerScope(Authentication auth){return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_WAREHOUSE")||a.getAuthority().equals("ROLE_ADMIN"))?null:Actor.id(auth);}
    private static String requestId(HttpServletRequest http){return (String)http.getAttribute("requestId");}
}