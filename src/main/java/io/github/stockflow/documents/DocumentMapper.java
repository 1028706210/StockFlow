package io.github.stockflow.documents;

import org.apache.ibatis.annotations.*;
import java.util.List;
import static io.github.stockflow.documents.DocumentModels.*;

@Mapper
public interface DocumentMapper {
    String RECEIPT_COLUMNS="id,actor_id,request_key,payload_hash,source,status,created_at,confirmed_at";
    String RETURN_COLUMNS="id,order_id,buyer_id,request_key,payload_hash,reason,status,created_at,confirmed_at";
    @Select("SELECT "+RECEIPT_COLUMNS+" FROM receipt_document WHERE actor_id=#{actor} AND request_key=#{key}")
    ReceiptRow receiptByKey(@Param("actor")long actor,@Param("key")String key);
    @Select("SELECT "+RECEIPT_COLUMNS+" FROM receipt_document WHERE id=#{id}") ReceiptRow receipt(long id);
    @Select("SELECT "+RECEIPT_COLUMNS+" FROM receipt_document WHERE id=#{id} FOR UPDATE") ReceiptRow lockReceipt(long id);
    @Insert("INSERT INTO receipt_document(actor_id,request_key,payload_hash,source) VALUES(#{actor},#{key},#{hash},#{source})")
    int createReceipt(@Param("actor")long actor,@Param("key")String key,@Param("hash")String hash,@Param("source")String source);
    @Insert("INSERT INTO receipt_item(receipt_id,sku_id,sku_code,sku_name,quantity) VALUES(#{id},#{sku},#{code},#{name},#{quantity})")
    int receiptItem(@Param("id")long id,@Param("sku")long sku,@Param("code")String code,@Param("name")String name,@Param("quantity")int quantity);
    @Select("SELECT id,sku_id,sku_code,sku_name,quantity FROM receipt_item WHERE receipt_id=#{id} ORDER BY sku_id") List<ReceiptItem> receiptItems(long id);
    @Update("UPDATE receipt_document SET status=#{state},confirmed_at=CASE WHEN #{state}='CONFIRMED' THEN CURRENT_TIMESTAMP(6) ELSE confirmed_at END WHERE id=#{id}")
    int receiptState(@Param("id")long id,@Param("state")String state);
    String RECEIPT_MATCH=" FROM receipt_document WHERE (source LIKE #{pattern} ESCAPE '!' OR CONVERT(id,CHAR CHARACTER SET utf8mb4) LIKE #{pattern} ESCAPE '!')<if test='status != &quot;all&quot;'> AND status=#{status}</if>";
    @Select("<script>SELECT COUNT(*)"+RECEIPT_MATCH+"</script>") long receiptCount(@Param("pattern")String pattern,@Param("status")String status);
    @Select("<script>SELECT "+RECEIPT_COLUMNS+RECEIPT_MATCH+" ORDER BY id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<ReceiptRow> receipts(@Param("pattern")String pattern,@Param("status")String status,@Param("offset")long offset,@Param("size")int size);

    @Select("SELECT "+RETURN_COLUMNS+" FROM customer_return WHERE buyer_id=#{buyer} AND request_key=#{key}")
    ReturnRow returnByKey(@Param("buyer")long buyer,@Param("key")String key);
    @Select("SELECT "+RETURN_COLUMNS+" FROM customer_return WHERE id=#{id}") ReturnRow returned(long id);
    @Select("SELECT "+RETURN_COLUMNS+" FROM customer_return WHERE id=#{id} FOR UPDATE") ReturnRow lockReturn(long id);
    @Select("SELECT COUNT(*) FROM customer_return WHERE order_id=#{id} AND status IN ('REQUESTED','CONFIRMED')") long activeReturns(long id);
    @Insert("INSERT INTO customer_return(order_id,buyer_id,request_key,payload_hash,reason) VALUES(#{order},#{buyer},#{key},#{hash},#{reason})")
    int createReturn(@Param("order")long order,@Param("buyer")long buyer,@Param("key")String key,@Param("hash")String hash,@Param("reason")String reason);
    @Insert("INSERT INTO return_item(return_id,sku_id,sku_code,sku_name,quantity,unit_price) SELECT #{id},sku_id,sku_code,sku_name,quantity,unit_price FROM order_item WHERE order_id=#{order} ORDER BY sku_id")
    int copyReturnItems(@Param("id")long id,@Param("order")long order);
    @Select("SELECT id,sku_id,sku_code,sku_name,quantity,unit_price FROM return_item WHERE return_id=#{id} ORDER BY sku_id") List<ReturnItem> returnItems(long id);
    @Update("UPDATE customer_return SET status=#{state},confirmed_at=CASE WHEN #{state}='CONFIRMED' THEN CURRENT_TIMESTAMP(6) ELSE confirmed_at END WHERE id=#{id}")
    int returnState(@Param("id")long id,@Param("state")String state);
    String RETURN_MATCH=" FROM customer_return WHERE (reason LIKE #{pattern} ESCAPE '!' OR CONVERT(id,CHAR CHARACTER SET utf8mb4) LIKE #{pattern} ESCAPE '!' OR CONVERT(order_id,CHAR CHARACTER SET utf8mb4) LIKE #{pattern} ESCAPE '!')<if test='buyer != null'> AND buyer_id=#{buyer}</if><if test='status != &quot;all&quot;'> AND status=#{status}</if>";
    @Select("<script>SELECT COUNT(*)"+RETURN_MATCH+"</script>") long returnCount(@Param("buyer")Long buyer,@Param("pattern")String pattern,@Param("status")String status);
    @Select("<script>SELECT "+RETURN_COLUMNS+RETURN_MATCH+" ORDER BY id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<ReturnRow> returns(@Param("buyer")Long buyer,@Param("pattern")String pattern,@Param("status")String status,@Param("offset")long offset,@Param("size")int size);
    @Insert("INSERT INTO inventory_ledger(sku_id,order_id,receipt_id,return_id,actor_id,operation_type,operation_key,quantity) VALUES(#{sku},#{order,jdbcType=BIGINT},#{receipt,jdbcType=BIGINT},#{returned,jdbcType=BIGINT},#{actor},#{type},#{key},#{quantity})")
    int ledger(@Param("sku")long sku,@Param("order")Long order,@Param("receipt")Long receipt,@Param("returned")Long returned,@Param("actor")long actor,@Param("type")String type,@Param("key")String key,@Param("quantity")int quantity);
}