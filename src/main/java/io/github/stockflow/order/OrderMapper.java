package io.github.stockflow.order;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;
import static io.github.stockflow.order.OrderModels.*;
@Mapper
public interface OrderMapper {
    String ROW_COLUMNS="id,buyer_id,request_key,payload_hash,status,total,expires_at,created_at,cancel_reason";
    String SUMMARY_COLUMNS="id,status,total,expires_at,buyer_id,cancel_reason";
    @Insert("INSERT INTO customer_order(buyer_id,request_key,payload_hash,status,expires_at) VALUES(#{buyer},#{key},#{hash},'PENDING_CONFIRM',DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 15 MINUTE))")
    int insert(@Param("buyer")long buyer,@Param("key")String key,@Param("hash")String hash);
    @Select("SELECT "+ROW_COLUMNS+" FROM customer_order WHERE buyer_id=#{buyer} AND request_key=#{key}")
    Row byKey(@Param("buyer")long buyer,@Param("key")String key);
    @Select("SELECT "+ROW_COLUMNS+" FROM customer_order WHERE id=#{id} AND buyer_id=#{buyer}")
    Row byId(@Param("buyer")long buyer,@Param("id")long id);
    @Select("SELECT "+ROW_COLUMNS+" FROM customer_order WHERE id=#{id}")
    Row anyById(long id);
    @Select("SELECT "+ROW_COLUMNS+" FROM customer_order WHERE id=#{id} AND buyer_id=#{buyer} FOR UPDATE")
    Row lockOwned(@Param("buyer")long buyer,@Param("id")long id);
    @Select("SELECT "+ROW_COLUMNS+" FROM customer_order WHERE id=#{id} FOR UPDATE")
    Row lockAny(long id);
    @Select("SELECT "+SUMMARY_COLUMNS+" FROM customer_order WHERE buyer_id=#{buyer} ORDER BY id DESC LIMIT 100")
    List<Summary> list(long buyer);
    @Select("SELECT "+SUMMARY_COLUMNS+" FROM customer_order ORDER BY id DESC LIMIT 100")
    List<Summary> fulfillmentList();
    @Select("SELECT id FROM customer_order WHERE status='PENDING_CONFIRM' AND expires_at<=CURRENT_TIMESTAMP(6) AND id>#{afterId} ORDER BY id LIMIT 100")
    List<Long> expiredIds(@Param("afterId")long afterId);
    @Select("SELECT expires_at<=CURRENT_TIMESTAMP(6) FROM customer_order WHERE id=#{id}")
    boolean expired(long id);
    @Update("UPDATE customer_order SET status='CONFIRMED' WHERE id=#{id} AND status='PENDING_CONFIRM' AND expires_at>CURRENT_TIMESTAMP(6)")
    int confirm(long id);
    @Update("UPDATE customer_order SET status=#{state},cancel_reason=#{reason} WHERE id=#{id}")
    int transition(@Param("id")long id,@Param("state")String state,@Param("reason")String reason);
    @Insert("INSERT INTO order_item(order_id,sku_id,quantity,unit_price) VALUES(#{order},#{sku},#{quantity},#{price})")
    int item(@Param("order")long order,@Param("sku")long sku,@Param("quantity")int quantity,@Param("price")BigDecimal price);
    @Update("UPDATE customer_order SET total=#{total} WHERE id=#{id}")
    int total(@Param("id")long id,@Param("total")BigDecimal total);
    @Select("SELECT sku_id,quantity,unit_price FROM order_item WHERE order_id=#{id} ORDER BY sku_id")
    List<Item> items(long id);
}
