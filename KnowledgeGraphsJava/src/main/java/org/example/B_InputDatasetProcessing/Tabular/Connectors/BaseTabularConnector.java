package org.example.B_InputDatasetProcessing.Tabular.Connectors;

import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

public interface BaseTabularConnector {

    Table retrieveDataFromTable(String tableName);
    boolean isJoin(String srcTable, String fkCol,
                   String tgtTable, String pkCol);

    Iterable<Row> selectRowsWithValue(String tableName, String column, String value);

    void closeConnection();
}
