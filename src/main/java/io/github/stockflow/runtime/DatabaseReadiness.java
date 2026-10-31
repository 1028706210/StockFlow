package io.github.stockflow.runtime;

import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.SQLException;

@Component
public class DatabaseReadiness {
    private final DataSource dataSource;
    public DatabaseReadiness(DataSource dataSource){this.dataSource=dataSource;}
    public boolean ready(){
        // Resolve required schema objects as well as the connection; SELECT 1 alone misses schema loss.
        try(var connection=dataSource.getConnection();var query=connection.prepareStatement(
            "SELECT h.success FROM flyway_schema_history h WHERE h.success=1 AND CAST(h.version AS UNSIGNED)=6 " +
            "AND NOT EXISTS(SELECT 1 FROM order_item WHERE sku_code IS NOT NULL OR sku_name IS NOT NULL LIMIT 0) LIMIT 1")){
            query.setQueryTimeout(2);
            try(var rows=query.executeQuery()){return rows.next();}
        }catch(SQLException|RuntimeException unavailable){return false;}
    }
}
