package io.github.stockflow.catalog;
import org.apache.ibatis.annotations.*;
import java.util.List;
import static io.github.stockflow.catalog.CatalogModels.*;
@Mapper
public interface CatalogMapper {
    @Insert("INSERT INTO sku(code,name,price) VALUES(#{code},#{name},#{price})") int insert(CreateSku sku);
    @Select("SELECT id,code,name,price,active FROM sku WHERE code=#{code}") Sku byCode(String code);
    @Select("SELECT id,code,name,price,active FROM sku WHERE id=#{id}") Sku byId(long id);
    @Select("SELECT id,code,name,price,active FROM sku WHERE id=#{id} FOR UPDATE") Sku lock(long id);
    @Update("UPDATE sku SET name=#{request.name},price=#{request.price},active=#{request.active} WHERE id=#{id}")
    int update(@Param("id")long id,@Param("request")UpdateSku request);
    @Select("SELECT id,code,name,price,active FROM sku ORDER BY id DESC LIMIT 100") List<Sku> list();
    @Insert("INSERT INTO inventory(sku_id) VALUES(#{id})") int initializeInventory(long id);
}
