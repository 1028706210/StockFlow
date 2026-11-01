package io.github.stockflow.catalog;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public final class CatalogModels {
    private CatalogModels(){}
    public record CreateSku(
        @NotBlank @Pattern(regexp="[A-Z0-9_-]{1,40}")String code,
        @NotBlank @Size(max=120)String name,
        @NotNull @DecimalMin("0.00") @DecimalMax("999999.99") @Digits(integer=6,fraction=2)BigDecimal price,
        @Size(max=40)String category,@Size(max=120)String specification,
        @Size(max=12)String unit,@Size(max=1000)String description){
        public CreateSku {name=name==null?null:name.trim();category=text(category,"");specification=text(specification,"");unit=text(unit,"件");description=text(description,"");}
    }
    public record Sku(long id,String code,String name,BigDecimal price,boolean active,String category,String specification,String unit,String description){}
    public record UpdateSku(
        @NotBlank @Size(max=120)String name,
        @NotNull @DecimalMin("0.00") @DecimalMax("999999.99") @Digits(integer=6,fraction=2)BigDecimal price,
        @NotNull Boolean active,@Size(max=40)String category,@Size(max=120)String specification,
        @Size(max=12)String unit,@Size(max=1000)String description){
        public UpdateSku {name=name==null?null:name.trim();category=optional(category);specification=optional(specification);unit=unit==null?null:text(unit,"件");description=optional(description);}
    }
    private static String text(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}
    private static String optional(String value){return value==null?null:value.trim();}
}
