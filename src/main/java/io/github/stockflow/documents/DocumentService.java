package io.github.stockflow.documents;

import io.github.stockflow.api.ApiException;
import io.github.stockflow.catalog.CatalogMapper;
import io.github.stockflow.inventory.InventoryMapper;
import io.github.stockflow.operations.AuditService;
import io.github.stockflow.order.OrderMapper;
import io.github.stockflow.workbench.WorkbenchModels.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static io.github.stockflow.documents.DocumentModels.*;

@Service
public class DocumentService {
    private final DocumentMapper mapper;
    private final InventoryMapper inventory;
    private final CatalogMapper catalog;
    private final OrderMapper orders;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    public DocumentService(DocumentMapper mapper,InventoryMapper inventory,CatalogMapper catalog,OrderMapper orders,AuditService audit,TransactionTemplate transaction){
        this.mapper=mapper;this.inventory=inventory;this.catalog=catalog;this.orders=orders;this.audit=audit;this.transaction=transaction;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Receipt receipt(long id){var row=mapper.receipt(id);if(row==null)throw ApiException.notFound();return receiptView(row);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Return returned(Long buyer,long id){return returnView(owned(mapper.returned(id),buyer));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Receipt> receipts(String q,String status,int page,int size){
        var pattern=pattern(q);long total=mapper.receiptCount(pattern,status),pages=Math.max(1,(total+size-1)/size);int current=(int)Math.min(page,pages);
        return new Page<>(mapper.receipts(pattern,status,(long)(current-1)*size,size).stream().map(this::receiptView).toList(),total,current,size,pages);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Return> returns(Long buyer,String q,String status,int page,int size){
        var pattern=pattern(q);long total=mapper.returnCount(buyer,pattern,status),pages=Math.max(1,(total+size-1)/size);int current=(int)Math.min(page,pages);
        return new Page<>(mapper.returns(buyer,pattern,status,(long)(current-1)*size,size).stream().map(this::returnView).toList(),total,current,size,pages);
    }
    public Creation<Receipt> createReceipt(long actor,CreateReceipt request,String requestId){
        String source=request.source().trim();var lines=new TreeMap<Long,Integer>();
        for(var line:request.items())if(lines.putIfAbsent(line.skuId(),line.quantity())!=null)throw new IllegalArgumentException("Receipt SKU must be unique");
        var canonical=new StringBuilder(source.length()+":"+source+";");lines.forEach((sku,quantity)->canonical.append(sku).append(':').append(quantity).append(';'));
        String hash=fingerprint(canonical.toString());var existing=mapper.receiptByKey(actor,request.requestKey());if(existing!=null)return replayReceipt(existing,hash);
        try{return transaction.execute(tx->{
            mapper.createReceipt(actor,request.requestKey(),hash,source);var row=mapper.receiptByKey(actor,request.requestKey());
            for(var line:lines.entrySet()){
                var sku=catalog.byId(line.getKey());if(sku==null)throw ApiException.notFound();
                mapper.receiptItem(row.id(),sku.id(),sku.code(),sku.name(),line.getValue());
            }
            var result=receiptView(row);audit.record(actor,"RECEIPT_CREATE","RECEIPT",row.id(),label(source),null,result,requestId);return new Creation<>(result,false);
        });}catch(DuplicateKeyException e){var row=mapper.receiptByKey(actor,request.requestKey());if(row==null)throw e;return replayReceipt(row,hash);}
    }
    public Creation<Return> createReturn(long buyer,CreateReturn request,String requestId){
        String reason=request.reason().trim(),hash=fingerprint(request.orderId()+":"+reason);
        var existing=mapper.returnByKey(buyer,request.requestKey());if(existing!=null)return replayReturn(existing,hash);
        try{return transaction.execute(tx->{
            // Every request for an order takes this lock, including distinct request keys.
            var order=orders.lockOwned(buyer,request.orderId());if(order==null)throw ApiException.notFound();
            var replay=mapper.returnByKey(buyer,request.requestKey());if(replay!=null)return replayReturn(replay,hash);
            if(!order.status().equals("COMPLETED"))throw ApiException.conflict("INVALID_ORDER_STATE","Only completed orders can be returned");
            if(mapper.activeReturns(order.id())>0)throw ApiException.conflict("RETURN_EXISTS","This order already has an active or confirmed return");
            mapper.createReturn(order.id(),buyer,request.requestKey(),hash,reason);var row=mapper.returnByKey(buyer,request.requestKey());
            if(mapper.copyReturnItems(row.id(),order.id())==0)throw ApiException.conflict("INVALID_ORDER_STATE","Order has no returnable items");
            var result=returnView(row);audit.record(buyer,"RETURN_CREATE","RETURN",row.id(),"Order #"+order.id(),null,result,requestId);return new Creation<>(result,false);
        });}catch(DuplicateKeyException e){var row=mapper.returnByKey(buyer,request.requestKey());if(row==null)throw e;return replayReturn(row,hash);}
    }
    @Transactional
    public Receipt confirmReceipt(long actor,long id,String requestId){
        var row=mapper.lockReceipt(id);if(row==null)throw ApiException.notFound();if(row.status().equals("CONFIRMED"))return receiptView(row);
        requireState(row.status(),"DRAFT","RECEIPT");var before=receiptView(row);
        for(var line:before.items()){
            receive(line.skuId(),line.quantity());
            ledger(line.skuId(),null,id,null,actor,"INBOUND","receipt:"+id,line.quantity());
        }
        mapper.receiptState(id,"CONFIRMED");var after=receiptView(mapper.receipt(id));audit.record(actor,"RECEIPT_CONFIRM","RECEIPT",id,label(row.source()),before,after,requestId);return after;
    }
    @Transactional
    public Receipt cancelReceipt(long actor,long id,String requestId){
        var row=mapper.lockReceipt(id);if(row==null)throw ApiException.notFound();if(row.status().equals("CANCELLED"))return receiptView(row);
        requireState(row.status(),"DRAFT","RECEIPT");var before=receiptView(row);mapper.receiptState(id,"CANCELLED");var after=receiptView(mapper.receipt(id));audit.record(actor,"RECEIPT_CANCEL","RECEIPT",id,label(row.source()),before,after,requestId);return after;
    }
    @Transactional
    public Return confirmReturn(long actor,long id,String requestId){
        var row=owned(mapper.lockReturn(id),null);if(row.status().equals("CONFIRMED"))return returnView(row);
        requireState(row.status(),"REQUESTED","RETURN");var before=returnView(row);
        for(var line:before.items()){
            receive(line.skuId(),line.quantity());
            ledger(line.skuId(),row.orderId(),null,id,actor,"RETURN_IN","return:"+id,line.quantity());
        }
        mapper.returnState(id,"CONFIRMED");var after=returnView(mapper.returned(id));audit.record(actor,"RETURN_CONFIRM","RETURN",id,"Order #"+row.orderId(),before,after,requestId);return after;
    }
    @Transactional
    public Return cancelReturn(long buyer,long id,String requestId){
        var row=owned(mapper.lockReturn(id),buyer);if(row.status().equals("CANCELLED"))return returnView(row);
        requireState(row.status(),"REQUESTED","RETURN");var before=returnView(row);mapper.returnState(id,"CANCELLED");var after=returnView(mapper.returned(id));audit.record(buyer,"RETURN_CANCEL","RETURN",id,"Order #"+row.orderId(),before,after,requestId);return after;
    }
    private void receive(long sku,int quantity){
        // Document item queries are always ordered by SKU, matching order inventory locks.
        if(inventory.lockId(sku)==null)throw ApiException.notFound();
        if(inventory.receive(sku,quantity)!=1)throw ApiException.conflict("INVENTORY_LIMIT","Inventory limit reached");
    }
    private void ledger(long sku,Long order,Long receipt,Long returned,long actor,String type,String key,int quantity){
        try{mapper.ledger(sku,order,receipt,returned,actor,type,key,quantity);}
        catch(DuplicateKeyException e){throw ApiException.conflict("INVENTORY_CONFLICT","Document ledger already exists; reconcile before retrying");}
    }
    private Creation<Receipt> replayReceipt(ReceiptRow row,String hash){checkHash(row.payloadHash(),hash);return new Creation<>(receiptView(row),true);}
    private Creation<Return> replayReturn(ReturnRow row,String hash){checkHash(row.payloadHash(),hash);return new Creation<>(returnView(row),true);}
    private static void checkHash(String saved,String supplied){if(!saved.equals(supplied))throw ApiException.conflict("IDEMPOTENCY_CONFLICT","Request key was used for different content");}
    private static ReturnRow owned(ReturnRow row,Long buyer){if(row==null||(buyer!=null&&row.buyerId()!=buyer))throw ApiException.notFound();return row;}
    private static void requireState(String actual,String expected,String type){if(!actual.equals(expected))throw ApiException.conflict("INVALID_"+type+"_STATE","Document cannot perform this action in its current state");}
    private Receipt receiptView(ReceiptRow row){return new Receipt(row.id(),row.source(),row.status(),row.actorId(),row.createdAt(),row.confirmedAt(),mapper.receiptItems(row.id()));}
    private Return returnView(ReturnRow row){return new Return(row.id(),row.orderId(),row.buyerId(),row.reason(),row.status(),row.createdAt(),row.confirmedAt(),mapper.returnItems(row.id()));}
    private static String pattern(String q){return "%"+q.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private static String label(String source){return source.substring(0,Math.min(200,source.length()));}
    private static String fingerprint(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 is required by the JDK",e);}
    }
}