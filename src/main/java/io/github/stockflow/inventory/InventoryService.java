package io.github.stockflow.inventory;
import io.github.stockflow.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import static io.github.stockflow.inventory.InventoryModels.*;
@Service
public class InventoryService {
    private final InventoryMapper mapper;private final TransactionTemplate transaction;private final io.github.stockflow.operations.AuditService audit;
    public InventoryService(InventoryMapper mapper,TransactionTemplate transaction,io.github.stockflow.operations.AuditService audit){this.mapper=mapper;this.transaction=transaction;this.audit=audit;}
    public List<Stock> list(){return mapper.list();}
    public Stock receive(long actor,long sku,Receipt request){
        var key=actor+":"+request.operationKey();
        if(mapper.get(sku)==null)throw ApiException.notFound();
        try{
            return transaction.execute(status->{
                mapper.ledger(sku,null,actor,"INBOUND",key,request.quantity());
                if(mapper.receive(sku,request.quantity())!=1)throw ApiException.conflict("INVENTORY_LIMIT","Inventory limit reached");
                return mapper.get(sku);
            });
        }catch(DuplicateKeyException e){
            var original=mapper.receiptQuantity(sku,key);
            if(original!=null && original==request.quantity())return mapper.get(sku);
            throw ApiException.conflict("IDEMPOTENCY_CONFLICT","Receipt key was used for different content");
        }
    }
    @org.springframework.transaction.annotation.Transactional
    public Stock policy(long actor,long skuId,StockPolicy policy,String requestId){
        if(mapper.lockId(skuId)==null)throw ApiException.notFound();var before=mapper.get(skuId);
        if(mapper.policy(skuId,policy.safetyStock())!=1)throw ApiException.notFound();
        var after=mapper.get(skuId);if(before.safetyStock()!=after.safetyStock())audit.record(actor,"STOCK_POLICY","SKU",skuId,after.code(),java.util.Map.of("safetyStock",before.safetyStock()),java.util.Map.of("safetyStock",after.safetyStock()),requestId);return after;
    }
    public LedgerPage ledger(String q,String type,Long skuId,Long beforeId,int limit){return ledger(q,type,skuId,beforeId,limit,io.github.stockflow.workbench.QueryRange.of(null,null,null,null));}
    public LedgerPage ledger(String q,String type,Long skuId,Long beforeId,int limit,io.github.stockflow.workbench.QueryRange range){
        var rows=mapper.ledgerPage(searchPattern(q),type,skuId,beforeId,limit+1,range);
        boolean more=rows.size()>limit;
        var items=List.copyOf(rows.subList(0,Math.min(rows.size(),limit)));
        return new LedgerPage(items,more?items.get(items.size()-1).id():null);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public AlertPage alerts(String q,Integer threshold,Long beforeId,int limit){
        var pattern=searchPattern(q);
        long total=mapper.alertCount(pattern,threshold);
        var rows=mapper.alertPage(pattern,threshold,beforeId,limit+1);
        boolean more=rows.size()>limit;
        var items=List.copyOf(rows.subList(0,Math.min(rows.size(),limit)));
        return new AlertPage(items,more?items.get(items.size()-1).skuId():null,total,threshold);
    }
    private static String searchPattern(String q){
        return "%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    }
}
