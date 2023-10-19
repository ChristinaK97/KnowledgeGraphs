package org.example.B_InputDatasetProcessing.Tabular;

import tech.tablesaw.api.Table;

import java.util.HashMap;

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
    public void closeConnection() {}
}
