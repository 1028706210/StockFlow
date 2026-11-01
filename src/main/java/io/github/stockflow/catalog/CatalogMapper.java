package io.github.stockflow.catalog;
import org.apache.ibatis.annotations.*;
import java.util.List;
import static io.github.stockflow.catalog.CatalogModels.*;
@Mapper
public interface CatalogMapper {
    String COLUMNS="id,code,name,price,active,category,specification,unit,description";
    @Insert("INSERT INTO sku(code,name,price,category,specification,unit,description) VALUES(#{code},#{name},#{price},#{category},#{specification},#{unit},#{description})") int insert(CreateSku sku);
    @Select("SELECT "+COLUMNS+" FROM sku WHERE code=#{code}") Sku byCode(String code);
    @Select("SELECT "+COLUMNS+" FROM sku WHERE id=#{id}") Sku byId(long id);
    @Select("SELECT "+COLUMNS+" FROM sku WHERE id=#{id} FOR UPDATE") Sku lock(long id);
    @Update("UPDATE sku SET name=#{request.name},price=#{request.price},active=#{request.active},category=COALESCE(#{request.category},category),specification=COALESCE(#{request.specification},specification),unit=COALESCE(#{request.unit},unit),description=COALESCE(#{request.description},description) WHERE id=#{id}")
    int update(@Param("id")long id,@Param("request")UpdateSku request);
    @Select("SELECT "+COLUMNS+" FROM sku ORDER BY id DESC LIMIT 100") List<Sku> list();
    @Insert("INSERT INTO inventory(sku_id) VALUES(#{id})") int initializeInventory(long id);
}
