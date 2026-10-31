package io.github.stockflow.operations;

import org.apache.ibatis.annotations.*;
import java.util.List;
import static io.github.stockflow.operations.OperationsModels.*;

@Mapper
public interface OperationsMapper {
    String SEARCH="(entity_label LIKE #{pattern} ESCAPE '!' OR CONVERT(entity_id,CHAR CHARACTER SET utf8mb4) LIKE #{pattern} ESCAPE '!')";
    String DATE_RANGE="<if test='range.from != null'> AND created_at &gt;= #{range.from}</if><if test='range.toExclusive != null'> AND created_at &lt; #{range.toExclusive}</if>";
    String AUDIT_DATE_RANGE="<if test='range.from != null'> AND a.created_at &gt;= #{range.from}</if><if test='range.toExclusive != null'> AND a.created_at &lt; #{range.toExclusive}</if>";
    String AUDIT_MATCH=" FROM operation_audit a LEFT JOIN app_user u ON u.id=a.actor_id WHERE "+SEARCH+"<if test='action != &quot;&quot;'> AND action=#{action}</if>";
    @Insert("INSERT INTO operation_audit(actor_id,action,entity_type,entity_id,entity_label,before_json,after_json,request_id) VALUES(#{actor},#{action},#{entityType},#{entityId},#{label},#{before,jdbcType=VARCHAR},#{after,jdbcType=VARCHAR},#{requestId})")
    int audit(@Param("actor")long actor,@Param("action")String action,@Param("entityType")String entityType,@Param("entityId")long entityId,@Param("label")String label,@Param("before")String before,@Param("after")String after,@Param("requestId")String requestId);
    @Select("<script>SELECT COUNT(*)"+AUDIT_MATCH+AUDIT_DATE_RANGE+"</script>") long auditCount(@Param("pattern")String pattern,@Param("action")String action,@Param("range")io.github.stockflow.workbench.QueryRange range);
    @Select("<script>SELECT a.id,a.actor_id,u.username,a.action,a.entity_type,a.entity_id,a.entity_label,a.before_json,a.after_json,a.request_id,a.created_at"+AUDIT_MATCH+AUDIT_DATE_RANGE+" ORDER BY a.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<AuditRow> audits(@Param("pattern")String pattern,@Param("action")String action,@Param("offset")long offset,@Param("size")int size,@Param("range")io.github.stockflow.workbench.QueryRange range);

    String LEDGER_SUM="SELECT sku_id,SUM(CASE WHEN operation_type IN ('INBOUND','ADJUST_IN','RETURN_IN') THEN quantity WHEN operation_type IN ('SHIP','ADJUST_OUT') THEN -quantity ELSE 0 END) physical,SUM(CASE WHEN operation_type='RESERVE' THEN quantity WHEN operation_type IN ('RELEASE','SHIP') THEN -quantity ELSE 0 END) held FROM inventory_ledger GROUP BY sku_id";
    String ORDER_SUM="SELECT oi.sku_id,SUM(oi.quantity) held FROM order_item oi JOIN customer_order o ON o.id=oi.order_id WHERE o.status IN ('PENDING_CONFIRM','CONFIRMED') GROUP BY oi.sku_id";
    String CONSISTENT="i.on_hand=COALESCE(l.physical,0) AND i.reserved=COALESCE(l.held,0) AND i.reserved=COALESCE(o.held,0)";
    String RECON_MATCH=" FROM inventory i JOIN sku s ON s.id=i.sku_id LEFT JOIN ("+LEDGER_SUM+") l ON l.sku_id=i.sku_id LEFT JOIN ("+ORDER_SUM+") o ON o.sku_id=i.sku_id WHERE "+io.github.stockflow.inventory.InventoryMapper.SEARCH;
    @Select("<script>SELECT COUNT(*)"+RECON_MATCH+"<if test='mismatchOnly'> AND NOT ("+CONSISTENT+")</if></script>")
    long reconciliationCount(@Param("pattern")String pattern,@Param("mismatchOnly")boolean mismatchOnly);
    @Select("<script>SELECT i.sku_id,s.code,s.name,i.on_hand,i.reserved,COALESCE(o.held,0) AS order_reserved,COALESCE(l.physical,0) AS ledger_on_hand,COALESCE(l.held,0) AS ledger_reserved,("+CONSISTENT+") AS consistent"+RECON_MATCH+"<if test='mismatchOnly'> AND NOT ("+CONSISTENT+")</if> ORDER BY i.sku_id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<Reconciliation> reconciliation(@Param("pattern")String pattern,@Param("mismatchOnly")boolean mismatchOnly,@Param("offset")long offset,@Param("size")int size);

    @Select("SELECT sku_id,on_hand,reserved,version FROM inventory WHERE sku_id=#{skuId} FOR UPDATE") InventorySnapshot lockInventory(long skuId);
    @Insert("INSERT INTO stocktake(sku_id,sku_code,sku_name,actor_id,request_key,target_on_hand,original_on_hand,original_reserved,inventory_version,reason) VALUES(#{skuId},#{code},#{name},#{actor},#{request.requestKey},#{request.targetOnHand},#{stock.onHand},#{stock.reserved},#{stock.version},#{request.reason})")
    int createStocktake(@Param("actor")long actor,@Param("skuId")long skuId,@Param("code")String code,@Param("name")String name,@Param("request")CreateStocktake request,@Param("stock")InventorySnapshot stock);
    String STOCKTAKE_COLUMNS="id,sku_id,sku_code,sku_name,actor_id,request_key,target_on_hand,original_on_hand,original_reserved,inventory_version,reason,status,confirmed_by,adjustment,created_at,confirmed_at";
    @Select("SELECT "+STOCKTAKE_COLUMNS+" FROM stocktake WHERE actor_id=#{actor} AND request_key=#{key}") Stocktake byKey(@Param("actor")long actor,@Param("key")String key);
    @Select("SELECT "+STOCKTAKE_COLUMNS+" FROM stocktake WHERE id=#{id}") Stocktake stocktake(long id);
    @Select("SELECT "+STOCKTAKE_COLUMNS+" FROM stocktake WHERE id=#{id} FOR UPDATE") Stocktake lockStocktake(long id);
    @Update("UPDATE inventory SET on_hand=#{target},version=version+1 WHERE sku_id=#{skuId} AND version=#{version} AND reserved<=#{target}")
    int adjust(@Param("skuId")long skuId,@Param("target")long target,@Param("version")long version);
    @Insert("INSERT INTO inventory_ledger(sku_id,stocktake_id,actor_id,operation_type,operation_key,quantity) VALUES(#{skuId},#{id},#{actor},#{type},CONCAT('stocktake:',#{id}),#{quantity})")
    int adjustmentLedger(@Param("id")long id,@Param("skuId")long skuId,@Param("actor")long actor,@Param("type")String type,@Param("quantity")int quantity);
    @Update("UPDATE stocktake SET status='CONFIRMED',confirmed_by=#{actor},adjustment=#{adjustment},confirmed_at=CURRENT_TIMESTAMP(6) WHERE id=#{id}")
    int confirm(@Param("id")long id,@Param("actor")long actor,@Param("adjustment")long adjustment);
    @Update("UPDATE stocktake SET status='CANCELLED' WHERE id=#{id}") int cancel(long id);
    String STOCKTAKE_MATCH=" FROM stocktake WHERE (sku_code LIKE #{pattern} ESCAPE '!' OR sku_name LIKE #{pattern} ESCAPE '!' OR CONVERT(id,CHAR CHARACTER SET utf8mb4) LIKE #{pattern} ESCAPE '!')<if test='status != &quot;all&quot;'> AND status=#{status}</if>";
    @Select("<script>SELECT COUNT(*)"+STOCKTAKE_MATCH+DATE_RANGE+"</script>") long stocktakeCount(@Param("pattern")String pattern,@Param("status")String status,@Param("range")io.github.stockflow.workbench.QueryRange range);
    @Select("<script>SELECT "+STOCKTAKE_COLUMNS+STOCKTAKE_MATCH+DATE_RANGE+" ORDER BY id DESC LIMIT #{size} OFFSET #{offset}</script>") List<Stocktake> stocktakes(@Param("pattern")String pattern,@Param("status")String status,@Param("offset")long offset,@Param("size")int size,@Param("range")io.github.stockflow.workbench.QueryRange range);

    @Select("SELECT DATE(created_at) day,COUNT(*) value FROM customer_order WHERE created_at>=DATE_SUB(CURRENT_DATE(),INTERVAL 6 DAY) AND created_at<DATE_ADD(CURRENT_DATE(),INTERVAL 1 DAY) AND (#{buyer,jdbcType=BIGINT} IS NULL OR buyer_id=#{buyer,jdbcType=BIGINT}) GROUP BY DATE(created_at)") List<DayCount> orderTrend(@Param("buyer")Long buyer);
    @Select("SELECT DATE(created_at) day,SUM(quantity) value FROM inventory_ledger WHERE operation_type=#{type} AND created_at>=DATE_SUB(CURRENT_DATE(),INTERVAL 6 DAY) AND created_at<DATE_ADD(CURRENT_DATE(),INTERVAL 1 DAY) GROUP BY DATE(created_at)") List<DayCount> stockTrend(String type);
    @Select("SELECT CURRENT_DATE()") java.time.LocalDate today();
}
