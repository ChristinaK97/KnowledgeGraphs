package org.example.B_InputDatasetProcessing.Tabular.Connectors;

import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

public class TabularFilesConnector implements BaseTabularConnector {

    private HashMap<String, Table> datasetTables = new HashMap<>();

    public void addTable(String tableName, Table table) {
        datasetTables.put(tableName, table);
    }

    @Override
    public Table retrieveDataFromTable(String tableName) {
        return datasetTables.get(tableName);
    }

    @Override
    public boolean isJoin(String srcTable, String fkCol, String tgtTable, String pkCol) {
        return  retrieveDataFromTable(srcTable).column(fkCol).asSet()       // address.person_key
        .equals(retrieveDataFromTable(tgtTable).column(pkCol).asSet());     // person.person_key
    }

    @Override
    public Iterable<Row> selectRowsWithValue(String tableName, String column, String value) {
        Table table = retrieveDataFromTable(tableName);
        return table.where(table.column(column).asStringColumn().isEqualTo(value));
    }


    @Override
    public void closeConnection() {}
}
