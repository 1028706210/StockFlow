package io.github.stockflow.runtime;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/** Adopt only the known V004 schema; Flyway owns every later change. */
@Configuration
public class StockFlowMigrations implements FlywayMigrationStrategy {
    @Override public void migrate(Flyway flyway){
        try(var connection=flyway.getConfiguration().getDataSource().getConnection()){
            var tables=new TreeSet<String>();
            try(var query=connection.prepareStatement("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()");var rows=query.executeQuery()){while(rows.next())tables.add(rows.getString(1));}
            if(!tables.isEmpty()&&!tables.contains("flyway_schema_history")){
                if(!knownLegacy(connection,tables))throw new IllegalStateException("Unknown or damaged schema: refusing automatic baseline; inspect the database before upgrading");
                flyway.baseline();
            }
        }catch(SQLException exception){throw new IllegalStateException("Cannot validate database migration state",exception);}
        flyway.migrate();
    }
    private boolean knownLegacy(Connection connection,Set<String> actualTables){
        try{
            String source=new ClassPathResource("db/migration/B004__legacy_schema.sql").getContentAsString(StandardCharsets.UTF_8);
            var expectedTables=new TreeSet<String>();var tablePattern=Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+) \\(\\s*(.*?)\\s*\\) ENGINE=InnoDB",Pattern.DOTALL).matcher(source);
            while(tablePattern.find()){
                String table=tablePattern.group(1),body=tablePattern.group(2);expectedTables.add(table);
                var expectedColumns=new TreeMap<String,String>();var checks=new TreeSet<String>();var unique=new TreeSet<String>();var keys=new ArrayList<String>();var foreign=new TreeSet<String>();
                for(String raw:body.split("\\r?\\n")){
                    String line=raw.trim();
                    var column=Pattern.compile("^(\\w+) (BIGINT|INT|VARCHAR\\(\\d+\\)|DECIMAL\\(\\d+,\\d+\\)|CHAR\\(\\d+\\)|DATETIME\\(6\\)|BOOLEAN|JSON)(.*)$").matcher(line);
                    if(column.matches()){
                        String type=column.group(2).equals("BOOLEAN")?"tinyint(1)":column.group(2).toLowerCase(Locale.ROOT);
                        expectedColumns.put(column.group(1),type+":"+(column.group(3).contains("NOT NULL")||column.group(3).contains("PRIMARY KEY")?"NO":"YES"));
                        if(column.group(3).contains("PRIMARY KEY"))unique.add(column.group(1));
                        if(column.group(3).contains("UNIQUE"))unique.add(column.group(1));
                    }
                    if(line.startsWith("CHECK("))checks.add(normalizeCheck(line.substring(6,line.lastIndexOf(')'))));
                    var primary=Pattern.compile("^(?:PRIMARY KEY|UNIQUE KEY \\w+)\\(([^)]+)\\).*").matcher(line);if(primary.matches())unique.add(primary.group(1));
                    var index=Pattern.compile("^KEY \\w+\\(([^)]+)\\).*").matcher(line);if(index.matches())keys.add(index.group(1));
                    var fk=Pattern.compile(".*FOREIGN KEY\\((\\w+)\\) REFERENCES (\\w+)\\((\\w+)\\).*").matcher(line);if(fk.matches())foreign.add(fk.group(1)+":"+fk.group(2)+":"+fk.group(3));
                }
                var columns=new TreeMap<String,String>();
                try(var statement=connection.prepareStatement("SELECT column_name,column_type,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=?")){statement.setString(1,table);try(var rows=statement.executeQuery()){while(rows.next())columns.put(rows.getString(1),rows.getString(2).toLowerCase(Locale.ROOT)+":"+rows.getString(3));}}
                if(!columns.equals(expectedColumns))throw new IllegalStateException("Legacy columns differ in "+table+": "+columns+" expected "+expectedColumns);
                var actualChecks=new TreeSet<String>();
                try(var statement=connection.prepareStatement("SELECT cc.check_clause FROM information_schema.check_constraints cc JOIN information_schema.table_constraints tc ON tc.constraint_schema=cc.constraint_schema AND tc.constraint_name=cc.constraint_name WHERE tc.table_schema=DATABASE() AND tc.table_name=? AND tc.constraint_type='CHECK'")){statement.setString(1,table);try(var rows=statement.executeQuery()){while(rows.next())actualChecks.add(normalizeCheck(rows.getString(1)));}}
                if(!actualChecks.equals(checks))throw new IllegalStateException("Legacy checks differ in "+table+": "+actualChecks+" expected "+checks);
                var actualForeign=new TreeSet<String>();
                try(var statement=connection.prepareStatement("SELECT column_name,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=? AND referenced_table_name IS NOT NULL")){statement.setString(1,table);try(var rows=statement.executeQuery()){while(rows.next())actualForeign.add(rows.getString(1)+":"+rows.getString(2)+":"+rows.getString(3));}}
                if(!actualForeign.equals(foreign))throw new IllegalStateException("Legacy foreign keys differ in "+table);
                var indexes=new TreeMap<String,String>();var uniqueIndexes=new TreeSet<String>();
                try(var statement=connection.prepareStatement("SELECT index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') cols FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? GROUP BY index_name,non_unique")){statement.setString(1,table);try(var rows=statement.executeQuery()){while(rows.next()){indexes.put(rows.getString(1),rows.getString(3));if(rows.getInt(2)==0)uniqueIndexes.add(rows.getString(3));}}}
                if(!uniqueIndexes.equals(unique)||!indexes.values().containsAll(keys))throw new IllegalStateException("Legacy indexes differ in "+table+": "+uniqueIndexes+" expected "+unique);
            }
            return actualTables.equals(expectedTables);
        }catch(Exception exception){throw new IllegalStateException("Cannot validate legacy schema",exception);}
    }
    private static String normalizeCheck(String clause){return clause.toLowerCase(Locale.ROOT).replace("\\","").replaceAll("_[a-z0-9]+(?=')","").replaceAll("[\\s`()]+","");}
}
