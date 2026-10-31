package io.github.stockflow.inventory;
import org.apache.ibatis.annotations.*;
import java.util.List;
import static io.github.stockflow.inventory.InventoryModels.*;
@Mapper
public interface InventoryMapper {
    @Select("SELECT i.sku_id,s.code,i.on_hand,i.reserved,i.on_hand-i.reserved AS available,i.safety_stock FROM inventory i JOIN sku s ON s.id=i.sku_id WHERE i.sku_id=#{id}") Stock get(long id);
    @Select("SELECT i.sku_id,s.code,i.on_hand,i.reserved,i.on_hand-i.reserved AS available,i.safety_stock FROM inventory i JOIN sku s ON s.id=i.sku_id ORDER BY i.sku_id DESC LIMIT 100") List<Stock> list();
    @Select("SELECT sku_id FROM inventory WHERE sku_id=#{id} FOR UPDATE") Long lockId(long id);
    @Update("UPDATE inventory SET safety_stock=#{safetyStock} WHERE sku_id=#{skuId}")
    int policy(@Param("skuId")long skuId,@Param("safetyStock")int safetyStock);
    @Update("UPDATE inventory SET on_hand=on_hand+#{quantity},version=version+1 WHERE sku_id=#{skuId} AND on_hand<=1000000000-#{quantity}")
    int receive(@Param("skuId")long skuId,@Param("quantity")int quantity);
    @Update("UPDATE inventory SET reserved=reserved+#{quantity},version=version+1 WHERE sku_id=#{skuId} AND on_hand-reserved>=#{quantity}")
    int reserve(@Param("skuId")long skuId,@Param("quantity")int quantity);
    @Update("UPDATE inventory SET reserved=reserved-#{quantity},version=version+1 WHERE sku_id=#{skuId} AND reserved>=#{quantity}")
    int release(@Param("skuId")long skuId,@Param("quantity")int quantity);
    @Update("UPDATE inventory SET on_hand=on_hand-#{quantity},reserved=reserved-#{quantity},version=version+1 WHERE sku_id=#{skuId} AND on_hand>=#{quantity} AND reserved>=#{quantity}")
    int ship(@Param("skuId")long skuId,@Param("quantity")int quantity);
    @Insert("INSERT INTO inventory_ledger(sku_id,order_id,actor_id,operation_type,operation_key,quantity) VALUES(#{skuId},#{orderId},#{actorId},#{type},#{key},#{quantity})")
    int ledger(@Param("skuId")long skuId,@Param("orderId")Long orderId,@Param("actorId")long actorId,@Param("type")String type,@Param("key")String key,@Param("quantity")int quantity);
    @Select("SELECT quantity FROM inventory_ledger WHERE sku_id=#{skuId} AND operation_type='INBOUND' AND operation_key=#{key}")
    Integer receiptQuantity(@Param("skuId")long skuId,@Param("key")String key);
    String SEARCH="(CONVERT(s.code USING utf8mb4) COLLATE utf8mb4_bin LIKE #{pattern} ESCAPE '!' OR s.name LIKE #{pattern} ESCAPE '!')";
    String ALERT_MATCH=" FROM inventory i JOIN sku s ON s.id=i.sku_id WHERE s.active=TRUE AND i.on_hand-i.reserved &lt; COALESCE(#{threshold,jdbcType=INTEGER},i.safety_stock) AND "+SEARCH;
    @Select("<script>SELECT l.id,l.sku_id,s.code,s.name,l.order_id,l.stocktake_id,l.receipt_id,l.return_id,l.actor_id,l.operation_type,l.quantity,l.created_at,"
        +"CASE l.operation_type WHEN 'INBOUND' THEN l.quantity WHEN 'ADJUST_IN' THEN l.quantity WHEN 'RETURN_IN' THEN l.quantity WHEN 'SHIP' THEN -l.quantity WHEN 'ADJUST_OUT' THEN -l.quantity ELSE 0 END AS on_hand_delta,"
        +"CASE l.operation_type WHEN 'RESERVE' THEN l.quantity WHEN 'RELEASE' THEN -l.quantity WHEN 'SHIP' THEN -l.quantity ELSE 0 END AS reserved_delta "
        +"FROM inventory_ledger l JOIN sku s ON s.id=l.sku_id WHERE "+SEARCH
        +"<if test='type != &quot;&quot;'> AND l.operation_type=#{type}</if>"
        +"<if test='skuId != null'> AND l.sku_id=#{skuId}</if>"
        +"<if test='range.from != null'> AND l.created_at &gt;= #{range.from}</if><if test='range.toExclusive != null'> AND l.created_at &lt; #{range.toExclusive}</if>"
        +"<if test='beforeId != null'> AND l.id &lt; #{beforeId}</if> ORDER BY l.id DESC LIMIT #{fetchLimit}</script>")
    List<LedgerEntry> ledgerPage(@Param("pattern")String pattern,@Param("type")String type,@Param("skuId")Long skuId,@Param("beforeId")Long beforeId,@Param("fetchLimit")int fetchLimit,@Param("range")io.github.stockflow.workbench.QueryRange range);
    @Select("<script>SELECT i.sku_id,s.code,s.name,i.on_hand,i.reserved,i.on_hand-i.reserved AS available,i.safety_stock,COALESCE(#{threshold,jdbcType=INTEGER},i.safety_stock) AS threshold,COALESCE(#{threshold,jdbcType=INTEGER},i.safety_stock)-(i.on_hand-i.reserved) AS shortage"+ALERT_MATCH
        +"<if test='beforeId != null'> AND i.sku_id &lt; #{beforeId}</if> ORDER BY i.sku_id DESC LIMIT #{fetchLimit}</script>")
    List<Alert> alertPage(@Param("pattern")String pattern,@Param("threshold")Integer threshold,@Param("beforeId")Long beforeId,@Param("fetchLimit")int fetchLimit);
    @Select("<script>SELECT COUNT(*)"+ALERT_MATCH+"</script>")
    long alertCount(@Param("pattern")String pattern,@Param("threshold")Integer threshold);
}
