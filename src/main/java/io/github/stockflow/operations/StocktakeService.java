package io.github.stockflow.operations;

import io.github.stockflow.api.ApiException;
import io.github.stockflow.catalog.CatalogMapper;
import io.github.stockflow.workbench.WorkbenchModels.Page;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static io.github.stockflow.operations.OperationsModels.*;

@Service
public class StocktakeService {
    private final OperationsMapper mapper;private final CatalogMapper catalog;private final AuditService audit;private final TransactionTemplate transaction;
    public StocktakeService(OperationsMapper mapper,CatalogMapper catalog,AuditService audit,TransactionTemplate transaction){this.mapper=mapper;this.catalog=catalog;this.audit=audit;this.transaction=transaction;}
    public Stocktake get(long id){var row=mapper.stocktake(id);if(row==null)throw ApiException.notFound();return row;}
    public Creation create(long actor,CreateStocktake input,String requestId){
        var request=new CreateStocktake(input.requestKey(),input.skuId(),input.targetOnHand(),input.reason().trim());
        var existing=mapper.byKey(actor,request.requestKey());if(existing!=null)return replay(existing,request);
        try{return transaction.execute(status->{
            var stock=mapper.lockInventory(request.skuId());if(stock==null)throw ApiException.notFound();var sku=catalog.byId(request.skuId());
            mapper.createStocktake(actor,sku.id(),sku.code(),sku.name(),request,stock);var row=mapper.byKey(actor,request.requestKey());
            audit.record(actor,"STOCKTAKE_CREATE","STOCKTAKE",row.id(),row.skuCode()+" · "+row.skuName(),null,row,requestId);return new Creation(row,false);
        });}catch(DuplicateKeyException e){var row=mapper.byKey(actor,request.requestKey());if(row==null)throw e;return replay(row,request);}
    }
    private Creation replay(Stocktake row,CreateStocktake request){if(row.skuId()!=request.skuId()||row.targetOnHand()!=request.targetOnHand()||!row.reason().equals(request.reason()))throw ApiException.conflict("IDEMPOTENCY_CONFLICT","Stocktake key was used for different content");return new Creation(row,true);}
    @Transactional public Stocktake confirm(long actor,long id,String requestId){
        var row=mapper.lockStocktake(id);if(row==null)throw ApiException.notFound();if(row.status().equals("CONFIRMED"))return row;
        if(!row.status().equals("DRAFT"))throw ApiException.conflict("INVALID_STOCKTAKE_STATE","Only draft stocktakes can be confirmed");
        var stock=mapper.lockInventory(row.skuId());
        if(stock.version()!=row.inventoryVersion())throw ApiException.conflict("STOCK_CHANGED","Inventory changed after this count was created; create a fresh count");
        if(row.targetOnHand()<stock.reserved())throw ApiException.conflict("RESERVED_STOCK","Counted stock cannot be below reserved stock");
        long difference=row.targetOnHand()-stock.onHand();
        if(difference!=0){
            if(mapper.adjust(row.skuId(),row.targetOnHand(),stock.version())!=1)throw ApiException.conflict("STOCK_CHANGED","Inventory changed; create a fresh count");
            try{mapper.adjustmentLedger(id,row.skuId(),actor,difference>0?"ADJUST_IN":"ADJUST_OUT",Math.toIntExact(Math.abs(difference)));}
            catch(DuplicateKeyException e){throw ApiException.conflict("INVENTORY_CONFLICT","Adjustment ledger already exists; reconcile this count before retrying");}
        }
        mapper.confirm(id,actor,difference);var result=get(id);audit.record(actor,"STOCKTAKE_CONFIRM","STOCKTAKE",id,row.skuCode()+" · "+row.skuName(),row,result,requestId);return result;
    }
    @Transactional public Stocktake cancel(long actor,long id,String requestId){
        var row=mapper.lockStocktake(id);if(row==null)throw ApiException.notFound();if(row.status().equals("CANCELLED"))return row;
        if(!row.status().equals("DRAFT"))throw ApiException.conflict("INVALID_STOCKTAKE_STATE","Confirmed stocktakes cannot be cancelled");
        mapper.cancel(id);var result=get(id);audit.record(actor,"STOCKTAKE_CANCEL","STOCKTAKE",id,row.skuCode()+" · "+row.skuName(),row,result,requestId);return result;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Stocktake> list(String q,String status,int page,int size){var pattern=AuditService.pattern(q);long total=mapper.stocktakeCount(pattern,status),pages=Math.max(1,(total+size-1)/size);int current=(int)Math.min(page,pages);return new Page<>(mapper.stocktakes(pattern,status,(long)(current-1)*size,size),total,current,size,pages);}
}
