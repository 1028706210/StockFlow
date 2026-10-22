package io.github.stockflow.inventory;
import org.apache.ibatis.annotations.*;
import java.util.List;
import static io.github.stockflow.inventory.InventoryModels.*;
@Mapper
public interface InventoryMapper {
    @Select("SELECT i.sku_id,s.code,i.on_hand,i.reserved,i.on_hand-i.reserved AS available FROM inventory i JOIN sku s ON s.id=i.sku_id WHERE i.sku_id=#{id}") Stock get(long id);
    @Select("SELECT i.sku_id,s.code,i.on_hand,i.reserved,i.on_hand-i.reserved AS available FROM inventory i JOIN sku s ON s.id=i.sku_id ORDER BY i.sku_id DESC LIMIT 100") List<Stock> list();
    @Update("UPDATE inventory SET on_hand=on_hand+#{quantity} WHERE sku_id=#{skuId} AND on_hand<=1000000000-#{quantity}")
    int receive(@Param("skuId")long skuId,@Param("quantity")int quantity);
    @Update("UPDATE inventory SET reserved=reserved+#{quantity} WHERE sku_id=#{skuId} AND on_hand-reserved>=#{quantity}")
    int reserve(@Param("skuId")long skuId,@Param("quantity")int quantity);
    @Insert("INSERT INTO inventory_ledger(sku_id,order_id,actor_id,operation_type,operation_key,quantity) VALUES(#{skuId},#{orderId},#{actorId},#{type},#{key},#{quantity})")
    int ledger(@Param("skuId")long skuId,@Param("orderId")Long orderId,@Param("actorId")long actorId,@Param("type")String type,@Param("key")String key,@Param("quantity")int quantity);
    @Select("SELECT quantity FROM inventory_ledger WHERE sku_id=#{skuId} AND operation_type='INBOUND' AND operation_key=#{key}")
    Integer receiptQuantity(@Param("skuId")long skuId,@Param("key")String key);
}
