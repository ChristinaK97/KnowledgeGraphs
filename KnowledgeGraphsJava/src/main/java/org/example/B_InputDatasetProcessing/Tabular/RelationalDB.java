package org.example.B_InputDatasetProcessing.Tabular;

import org.example.B_InputDatasetProcessing.Tabular.Connectors.BaseTabularConnector;
import org.example.B_InputDatasetProcessing.Tabular.Connectors.SQLConnector;
import org.example.B_InputDatasetProcessing.Tabular.Connectors.TabularFilesConnector;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;

import static org.example.A_Coordinator.Pipeline.config;
import static org.example.A_Coordinator.config.Config.DEV_MODE;

public class RelationalDB implements Iterable<RTable> {
    private HashMap<String, RTable> rTables = new HashMap<>();
    private BaseTabularConnector connector;

    public RelationalDB(){
        if(config.In.isSQL()) {
            connector =  new SQLConnector();
            retrieveSchema();
        }else {
            connector = new TabularFilesConnector();
        }
    }

    public void closeConnection() {
        connector.closeConnection();
    }


    /** For single table like files like CSV or exported sql db**/
    public void addTable(String tableName, String filename, Table inputTable,
                         HashMap<String,String> colTypes, String PKcol)
    {
        RTable table = new RTable(tableName, filename);
        table.addPK(PKcol);
        for(String colName : inputTable.columnNames())  // table columns
            table.addColumn(colName, colTypes.get(colName));
        table.setTableOperations();
        rTables.put(tableName, table);
        ((TabularFilesConnector) connector).addTable(tableName, inputTable);
    }


    public void retrieveSchema() {
        try {
            ResultSet tables = ((SQLConnector) connector).retrieveTables();
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                RTable table = new RTable(tableName, tableName);

                // table columns
                ResultSet columns = ((SQLConnector) connector).retrieveTableColumns(tableName);
                while (columns.next())
                    table.addColumn(
                            columns.getString("COLUMN_NAME"),
                            columns.getString("TYPE_NAME")
                    );
                columns.close();

                // PKs
                ResultSet PKs = ((SQLConnector) connector).retrieveTablePKs(tableName);
                while (PKs.next())
                    table.addPK(PKs.getString("COLUMN_NAME"));
                PKs.close();

                // FKs
                ResultSet FKs = ((SQLConnector) connector).retrieveTableFKs(tableName);
                while (FKs.next())
                    table.addFK(
                            FKs.getString("FKCOLUMN_NAME"),
                            FKs.getString("PKTABLE_NAME"),
                            FKs.getString("PKCOLUMN_NAME")
                    );
                FKs.close();

                table.setTableOperations();
                rTables.put(tableName, table);
            }
            tables.close();
        }catch (SQLException e) {
            e.printStackTrace();
        }
        if(DEV_MODE) System.out.println(rTables.toString());
    }

    public Iterator<RTable> iterator() {
        return rTables.values().iterator();
    }

    public HashMap<String, RTable> getrTables() {
        return rTables;
    }
    public RTable getTable(String tableName) {
        return rTables.get(tableName);
    }

    public boolean isPK(String tableName, String columnName){
        return rTables.get(tableName).isPK(columnName);
    }

    // queries
    public Table retrieveDataFromTable(String tableName) {
        return connector.retrieveDataFromTable(tableName);
    }

    public boolean isJoin(String srcTable, String fkCol,
                          String tgtTable, String pkCol) {
        return connector.isJoin(srcTable, fkCol,
                                tgtTable, pkCol);
    }

    public Iterable<Row> selectRowsWithValue(String tableName, String column, String value) {
        return connector.selectRowsWithValue(tableName, column, value);
    }
    //---------------------------------------------------------------------------------------
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DBSchema{\n");
        rTables.forEach((tableName, rTable) -> {
            sb.append(tableName).append("\n").append(rTable.toString()).append("\n");
        });
        sb.append("}");
        return sb.toString();
    }
}
