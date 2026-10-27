package io.github.stockflow.workbench;

import io.github.stockflow.catalog.CatalogModels.Sku;
import io.github.stockflow.inventory.InventoryMapper;
import io.github.stockflow.order.OrderMapper;
import io.github.stockflow.order.OrderModels.Summary;
import org.apache.ibatis.annotations.*;
import java.util.List;
import static io.github.stockflow.workbench.WorkbenchModels.*;

@Mapper
public interface WorkbenchMapper {
    String SKU_MATCH=" FROM sku s WHERE "+InventoryMapper.SEARCH
        +"<if test='status == &quot;active&quot;'> AND s.active=TRUE</if>"
        +"<if test='status == &quot;inactive&quot;'> AND s.active=FALSE</if>";
    String STOCK_COLUMNS="i.sku_id,s.code,s.name,s.price,s.active,i.on_hand,i.reserved,i.on_hand-i.reserved AS available,i.safety_stock";
    String STOCK_MATCH=" FROM inventory i JOIN sku s ON s.id=i.sku_id WHERE "+InventoryMapper.SEARCH
        +"<if test='status == &quot;empty&quot;'> AND i.on_hand-i.reserved &lt;= 0</if>"
        +"<if test='status == &quot;low&quot;'> AND i.on_hand-i.reserved &gt; 0 AND i.on_hand-i.reserved &lt; i.safety_stock</if>"
        +"<if test='status == &quot;healthy&quot;'> AND i.on_hand-i.reserved &gt;= i.safety_stock</if>";
    String ORDER_MATCH=" FROM customer_order WHERE (CONVERT(id,CHAR CHARACTER SET utf8mb4) LIKE #{pattern} ESCAPE '!'"
        +" OR CONCAT('SF-',id) LIKE #{pattern} ESCAPE '!'"
        +"<if test='buyer == null'> OR CONVERT(buyer_id,CHAR CHARACTER SET utf8mb4) LIKE #{pattern} ESCAPE '!'</if>)"
        +"<if test='buyer != null'> AND buyer_id=#{buyer}</if>"
        +"<if test='status != &quot;all&quot;'> AND status=#{status}</if>";
    String ORDER_RANGE="<if test='range.from != null'> AND created_at &gt;= #{range.from}</if><if test='range.toExclusive != null'> AND created_at &lt; #{range.toExclusive}</if><if test='range.minTotal != null'> AND total &gt;= #{range.minTotal}</if><if test='range.maxTotal != null'> AND total &lt;= #{range.maxTotal}</if>";
    @Select("<script>SELECT COUNT(*)"+SKU_MATCH+"</script>")
    long skuCount(@Param("pattern")String pattern,@Param("status")String status);
    @Select("<script>SELECT s.id,s.code,s.name,s.price,s.active"+SKU_MATCH+" ORDER BY s.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<Sku> skus(@Param("pattern")String pattern,@Param("status")String status,@Param("offset")long offset,@Param("size")int size);
    @Select("<script>SELECT COUNT(*)"+STOCK_MATCH+"</script>")
    long stockCount(@Param("pattern")String pattern,@Param("status")String status);
    @Select("<script>SELECT "+STOCK_COLUMNS+STOCK_MATCH+" ORDER BY i.sku_id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<StockRow> stocks(@Param("pattern")String pattern,@Param("status")String status,@Param("offset")long offset,@Param("size")int size);
    @Select("SELECT "+STOCK_COLUMNS+" FROM inventory i JOIN sku s ON s.id=i.sku_id WHERE i.sku_id=#{skuId}")
    StockRow stock(long skuId);
    @Select("<script>SELECT COUNT(*)"+ORDER_MATCH+ORDER_RANGE+"</script>")
    long orderCount(@Param("buyer")Long buyer,@Param("pattern")String pattern,@Param("status")String status,@Param("range")QueryRange range);
    @Select("<script>SELECT "+OrderMapper.SUMMARY_COLUMNS+ORDER_MATCH+ORDER_RANGE+" ORDER BY id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<Summary> orders(@Param("buyer")Long buyer,@Param("pattern")String pattern,@Param("status")String status,@Param("offset")long offset,@Param("size")int size,@Param("range")QueryRange range);
    @Select("SELECT (SELECT COUNT(*) FROM sku) AS product_count,"
        +"(SELECT COALESCE(SUM(on_hand),0) FROM inventory) AS on_hand,"
        +"(SELECT COALESCE(SUM(reserved),0) FROM inventory) AS reserved,"
        +"(SELECT COUNT(*) FROM inventory i JOIN sku s ON s.id=i.sku_id WHERE s.active=TRUE AND i.on_hand-i.reserved<i.safety_stock) AS low_stock_count,"
        +"COUNT(*) AS order_count,COALESCE(SUM(status='PENDING_CONFIRM'),0) AS pending_count,COALESCE(SUM(total),0) AS order_total,COALESCE(SUM(status='CONFIRMED'),0) AS confirmed_count,COALESCE(SUM(status='SHIPPED'),0) AS shipped_count FROM customer_order")
    Dashboard warehouseDashboard();
    @Select("SELECT (SELECT COUNT(*) FROM sku WHERE active=TRUE) AS product_count,0 AS on_hand,0 AS reserved,0 AS low_stock_count,"
        +"COUNT(*) AS order_count,COALESCE(SUM(status='PENDING_CONFIRM'),0) AS pending_count,COALESCE(SUM(total),0) AS order_total,COALESCE(SUM(status='CONFIRMED'),0) AS confirmed_count,COALESCE(SUM(status='SHIPPED'),0) AS shipped_count FROM customer_order WHERE buyer_id=#{buyer}")
    Dashboard buyerDashboard(long buyer);
}
