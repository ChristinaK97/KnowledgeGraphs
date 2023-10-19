package org.example.B_InputDatasetProcessing.Tabular;

import tech.tablesaw.api.Table;

public interface BaseTabularConnector {

    Table retrieveDataFromTable(String tableName);

    void closeConnection();
}
