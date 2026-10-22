package io.github.stockflow.order;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;
import static io.github.stockflow.order.OrderModels.*;
@Mapper
public interface OrderMapper {
    @Insert("INSERT INTO customer_order(buyer_id,request_key,payload_hash,status,expires_at) VALUES(#{buyer},#{key},#{hash},'PENDING_CONFIRM',DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 15 MINUTE))")
    int insert(@Param("buyer")long buyer,@Param("key")String key,@Param("hash")String hash);
    @Select("SELECT id,buyer_id,request_key,payload_hash,status,total,expires_at,created_at FROM customer_order WHERE buyer_id=#{buyer} AND request_key=#{key}")
    Row byKey(@Param("buyer")long buyer,@Param("key")String key);
    @Select("SELECT id,buyer_id,request_key,payload_hash,status,total,expires_at,created_at FROM customer_order WHERE id=#{id} AND buyer_id=#{buyer}")
    Row byId(@Param("buyer")long buyer,@Param("id")long id);
    @Select("SELECT id,status,total,expires_at FROM customer_order WHERE buyer_id=#{buyer} ORDER BY id DESC LIMIT 100")
    List<Summary> list(long buyer);
    @Insert("INSERT INTO order_item(order_id,sku_id,quantity,unit_price) VALUES(#{order},#{sku},#{quantity},#{price})")
    int item(@Param("order")long order,@Param("sku")long sku,@Param("quantity")int quantity,@Param("price")BigDecimal price);
    @Update("UPDATE customer_order SET total=#{total} WHERE id=#{id}")
    int total(@Param("id")long id,@Param("total")BigDecimal total);
    @Select("SELECT sku_id,quantity,unit_price FROM order_item WHERE order_id=#{id} ORDER BY sku_id")
    List<Item> items(long id);
}
