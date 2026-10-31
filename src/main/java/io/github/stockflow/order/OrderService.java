package io.github.stockflow.order;
import io.github.stockflow.api.ApiException;
import io.github.stockflow.catalog.CatalogMapper;
import io.github.stockflow.inventory.InventoryMapper;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import static io.github.stockflow.order.OrderModels.*;

@Service
public class OrderService {
    private final OrderMapper orders;private final CatalogMapper catalog;private final InventoryMapper inventory;private final TransactionTemplate transaction;
    public OrderService(OrderMapper orders,CatalogMapper catalog,InventoryMapper inventory,TransactionTemplate transaction){
        this.orders=orders;this.catalog=catalog;this.inventory=inventory;this.transaction=transaction;
    }
    public List<Summary> list(long buyer){return orders.list(buyer);}
    public View get(long buyer,long id){var row=orders.byId(buyer,id);if(row==null)throw ApiException.notFound();return view(row);}
    public List<Summary> fulfillmentList(){return orders.fulfillmentList();}
    public View fulfillmentGet(long id){var row=orders.anyById(id);if(row==null)throw ApiException.notFound();return view(row);}
    public View confirm(long buyer,long id){
        return transaction.execute(status->{
            var row=ownedForUpdate(buyer,id);
            if(row.status().equals("CONFIRMED")||row.status().equals("SHIPPED")||row.status().equals("COMPLETED"))return view(row);
            requireState(row,"PENDING_CONFIRM");
            if(orders.confirm(id)!=1)throw ApiException.conflict("ORDER_EXPIRED","Order confirmation deadline has passed");
            return view(orders.byId(buyer,id));
        });
    }
    public View cancel(long buyer,long id){
        return transaction.execute(status->{
            var row=ownedForUpdate(buyer,id);
            if(row.status().equals("CANCELLED"))return view(row);
            requireState(row,"PENDING_CONFIRM");
            release(row,buyer,"USER");
            return view(orders.byId(buyer,id));
        });
    }
    public View complete(long buyer,long id){
        return transaction.execute(status->{
            var row=ownedForUpdate(buyer,id);
            if(row.status().equals("COMPLETED"))return view(row);
            requireState(row,"SHIPPED");
            orders.transition(id,"COMPLETED",null);
            return view(orders.byId(buyer,id));
        });
    }
    public View ship(long actor,long id){
        return transaction.execute(status->{
            var row=orders.lockAny(id);
            if(row==null)throw ApiException.notFound();
            if(row.status().equals("SHIPPED")||row.status().equals("COMPLETED"))return view(row);
            requireState(row,"CONFIRMED");
            // items() returns SKU order, shared by creation, cancellation and shipping.
            for(var item:orders.items(id)){
                if(inventory.ship(item.skuId(),item.quantity())!=1)
                    throw ApiException.conflict("INVENTORY_CONFLICT","Reserved inventory is inconsistent");
                inventory.ledger(item.skuId(),id,actor,"SHIP","order:"+id,item.quantity());
            }
            orders.transition(id,"SHIPPED",null);
            return view(orders.anyById(id));
        });
    }
    public void expire(long id){
        transaction.executeWithoutResult(status->{
            var row=orders.lockAny(id);
            if(row!=null&&row.status().equals("PENDING_CONFIRM")&&orders.expired(id))
                release(row,row.buyerId(),"EXPIRED");
        });
    }
    public Creation create(long buyer,CreateOrder request){
        var lines=OrderLines.normalize(request.items());var hash=OrderLines.fingerprint(lines);
        var existing=orders.byKey(buyer,request.requestKey());
        if(existing!=null)return replay(existing,hash);
        for(int attempt=0;attempt<3;attempt++){
            try{
                return transaction.execute(status->{
                    orders.insert(buyer,request.requestKey(),hash);
                    var order=orders.byKey(buyer,request.requestKey());
                    var total=BigDecimal.ZERO;
                    for(var line:lines){
                        var sku=catalog.byId(line.skuId());
                        if(sku==null || !sku.active())throw ApiException.notFound();
                        if(inventory.reserve(sku.id(),line.quantity())!=1)
                            throw ApiException.conflict("INSUFFICIENT_STOCK","Insufficient available stock");
                        orders.item(order.id(),sku.id(),line.quantity(),sku.price(),sku.code(),sku.name());
                        inventory.ledger(sku.id(),order.id(),buyer,"RESERVE","order:"+order.id(),line.quantity());
                        total=total.add(sku.price().multiply(BigDecimal.valueOf(line.quantity())));
                    }
                    orders.total(order.id(),total);
                    return new Creation(view(orders.byKey(buyer,request.requestKey())),false);
                });
            }catch(DuplicateKeyException e){
                var committed=orders.byKey(buyer,request.requestKey());
                if(committed==null)throw e;
                return replay(committed,hash);
            }catch(PessimisticLockingFailureException e){
                if(attempt==2)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"RETRY_LATER","Database contention; retry this request key");
                LockSupport.parkNanos((attempt+1)*5_000_000L);
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }
    private Creation replay(Row row,String hash){
        if(!row.payloadHash().equals(hash))throw ApiException.conflict("IDEMPOTENCY_CONFLICT","Request key was used for different content");
        return new Creation(view(row),true);
    }
    private Row ownedForUpdate(long buyer,long id){
        var row=orders.lockOwned(buyer,id);
        if(row==null)throw ApiException.notFound();
        return row;
    }
    private void requireState(Row row,String expected){
        if(!row.status().equals(expected))throw ApiException.conflict("INVALID_ORDER_STATE","Order cannot perform this action in its current state");
    }
    private void release(Row row,long actor,String reason){
        for(var item:orders.items(row.id())){
            if(inventory.release(item.skuId(),item.quantity())!=1)
                throw ApiException.conflict("INVENTORY_CONFLICT","Reserved inventory is inconsistent");
            inventory.ledger(item.skuId(),row.id(),actor,"RELEASE","order:"+row.id(),item.quantity());
        }
        orders.transition(row.id(),"CANCELLED",reason);
    }
    private View view(Row row){return new View(row.id(),row.status(),row.total(),row.expiresAt(),orders.items(row.id()),row.buyerId(),row.cancelReason(),row.createdAt(),row.confirmedAt(),row.shippedAt(),row.completedAt(),row.cancelledAt());}
}
