package io.github.stockflow;

import io.github.stockflow.runtime.StockFlowMigrations;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="STOCKFLOW_MIGRATION_TEST_DB_URL",matches=".+")
class MigrationIntegrationTest {
    DriverManagerDataSource dataSource;
    @BeforeEach void isolatedFixture()throws Exception{
        dataSource=new DriverManagerDataSource(System.getenv("STOCKFLOW_MIGRATION_TEST_DB_URL"),System.getenv("STOCKFLOW_TEST_DB_USERNAME"),System.getenv("STOCKFLOW_TEST_DB_PASSWORD"));
        resetFixture();
    }
    @AfterEach void cleanup()throws Exception{resetFixture();}
    void resetFixture()throws Exception{
        try(var c=dataSource.getConnection();var s=c.createStatement()){
            try(var r=s.executeQuery("SELECT DATABASE()")){assertTrue(r.next());assertEquals("stockflow_test_migration",r.getString(1),"Refuse destructive fixtures outside dedicated migration schema");}
            List<String> tables=new ArrayList<>();try(var r=s.executeQuery("SHOW FULL TABLES WHERE Table_type='BASE TABLE'")){while(r.next())tables.add(r.getString(1));}
            s.execute("SET FOREIGN_KEY_CHECKS=0");try{for(String table:tables){assertTrue(table.matches("[A-Za-z0-9_]+"));s.execute("DROP TABLE `"+table+"`");}}finally{s.execute("SET FOREIGN_KEY_CHECKS=1");}
        }
    }
    Flyway flyway(){return Flyway.configure().dataSource(dataSource).baselineVersion("4").baselineOnMigrate(false).cleanDisabled(true).load();}
    void migrate(){new StockFlowMigrations().migrate(flyway());}
    long scalar(String sql)throws Exception{try(var c=dataSource.getConnection();var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getLong(1);}}
    void sql(String sql)throws Exception{try(var c=dataSource.getConnection();var s=c.createStatement()){s.execute(sql);}}
    void legacy()throws Exception{try(var c=dataSource.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/B004__legacy_schema.sql"));}}
    @Test void freshSchemaMigratesAndRestartIsNoOp()throws Exception{
        migrate();assertEquals(1,scalar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='order_item' AND COLUMN_NAME='sku_name'"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE CAST(version AS UNSIGNED)=6 AND success=1"));
        long history=scalar("SELECT COUNT(*) FROM flyway_schema_history");migrate();assertEquals(history,scalar("SELECT COUNT(*) FROM flyway_schema_history"));
    }
    @Test void knownLegacySchemaPreservesBusinessDataAndUnknownSnapshots()throws Exception{
        legacy();sql("INSERT INTO sku(id,code,name,price) VALUES(9001,'MIGRATE-KEEP','Original product',12.50)");
        sql("INSERT INTO inventory(sku_id,on_hand,reserved) VALUES(9001,9,0)");
        sql("INSERT INTO customer_order(id,buyer_id,request_key,payload_hash,status,total,expires_at) VALUES(9001,1,'migration-keep','hash','COMPLETED',12.50,NOW())");
        sql("INSERT INTO order_item(order_id,sku_id,quantity,unit_price) VALUES(9001,9001,1,12.50)");
        migrate();assertEquals(9,scalar("SELECT on_hand FROM inventory WHERE sku_id=9001"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM order_item WHERE order_id=9001 AND sku_code IS NULL AND sku_name IS NULL"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE type='BASELINE' AND version='4'"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE CAST(version AS UNSIGNED)=6 AND success=1"));
    }
    @Test void arbitraryOrDamagedPopulatedSchemaIsNeverBaselined()throws Exception{
        sql("CREATE TABLE unrelated(id INT PRIMARY KEY)");assertThrows(RuntimeException.class,this::migrate);
        assertEquals(0,scalar("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='flyway_schema_history'"));
        resetFixture();legacy();sql("ALTER TABLE inventory DROP CHECK inventory_chk_1");
        assertThrows(RuntimeException.class,this::migrate);
        assertEquals(0,scalar("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='flyway_schema_history'"));
    }
}
