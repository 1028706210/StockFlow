package io.github.stockflow.workbench;

import io.github.stockflow.auth.Actor;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController @Validated @RequestMapping("/api")
public class FilteredExportController {
    private final WorkbenchService service;
    public FilteredExportController(WorkbenchService service){this.service=service;}
    @GetMapping("/orders/export") @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<String> orders(Authentication auth,@RequestParam(defaultValue="") @Size(max=120)String q,@RequestParam(defaultValue="all") @Pattern(regexp="all|PENDING_CONFIRM|CONFIRMED|SHIPPED|COMPLETED|CANCELLED")String status,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to,@RequestParam(required=false)BigDecimal minTotal,@RequestParam(required=false)BigDecimal maxTotal){return orderCsv(Actor.id(auth),q,status,QueryRange.of(from,to,minTotal,maxTotal));}
    @GetMapping("/fulfillment/orders/export") @PreAuthorize("hasRole('WAREHOUSE')")
    public ResponseEntity<String> fulfillment(@RequestParam(defaultValue="") @Size(max=120)String q,@RequestParam(defaultValue="all") @Pattern(regexp="all|PENDING_CONFIRM|CONFIRMED|SHIPPED|COMPLETED|CANCELLED")String status,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to,@RequestParam(required=false)BigDecimal minTotal,@RequestParam(required=false)BigDecimal maxTotal){return orderCsv(null,q,status,QueryRange.of(from,to,minTotal,maxTotal));}
    @GetMapping("/inventory/export") @PreAuthorize("hasRole('WAREHOUSE')")
    public ResponseEntity<String> inventory(@RequestParam(defaultValue="") @Size(max=120)String q,@RequestParam(defaultValue="all") @Pattern(regexp="all|healthy|low|empty")String status){
        var out=new StringBuilder("\uFEFF商品编码,商品名称,实存,预留,可用,安全库存\r\n");
        for(var row:service.exportStocks(q,status))line(out,row.code(),row.name(),row.onHand(),row.reserved(),row.available(),row.safetyStock());
        return response("inventory.csv",out);
    }
    private ResponseEntity<String> orderCsv(Long buyer,String q,String status,QueryRange range){
        var out=new StringBuilder("\uFEFF订单号,状态,金额,确认截止时间,买家编号,取消原因\r\n");
        for(var row:service.exportOrders(buyer,q,status,range))line(out,"SF-"+row.id(),row.status(),row.total(),row.expiresAt(),row.buyerId(),row.cancelReason());
        return response("orders.csv",out);
    }
    private static void line(StringBuilder out,Object...values){
        var cells=new ArrayList<String>();for(var value:values){String text=value==null?"":String.valueOf(value);String leading=text.stripLeading();if(!leading.isEmpty()&&"=+-@".indexOf(leading.charAt(0))>=0||text.startsWith("\t")||text.startsWith("\r"))text="'"+text;cells.add("\""+text.replace("\"","\"\"")+"\"");}out.append(String.join(",",cells)).append("\r\n");
    }
    private static ResponseEntity<String> response(String filename,StringBuilder content){return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename+"\"").header(HttpHeaders.CACHE_CONTROL,"no-store").body(content.toString());}
}
