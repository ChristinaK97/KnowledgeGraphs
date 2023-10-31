package org.example.B_InputDatasetProcessing.Tabular.Connectors;
import static org.example.A_Coordinator.Pipeline.config;

import com.github.jsonldjava.shaded.com.google.common.collect.ImmutableMap;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;

public class SQLConnector implements BaseTabularConnector {

    private String schema;
    private Connection connection;
    private DatabaseMetaData metadata;
    private static HashMap<Integer, ColumnType> type = initializeMap();

    public SQLConnector() {
        if(config != null)
            connect();
        else // for testing
            tstConnect();
    }

    private void connect() {
        try {
            String url = config.In.credentials.URL;
            this.schema = url.substring(url.lastIndexOf('/') + 1);
            connection = DriverManager.getConnection(url, config.In.credentials.User,
                                                          config.In.credentials.Password);
            metadata = connection.getMetaData();

            if (connection == null)
                throw new SQLException("Failed to connect");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    // for testing
    private void tstConnect() {
        String url = "jdbc:mysql://localhost:3306/epibank";
        String user = "root";
        String password = "admin";
        try {
            this.schema = url.substring(url.lastIndexOf('/') + 1);
            connection = DriverManager.getConnection(url, user, password);
            metadata = connection.getMetaData();

            if (connection == null)
                throw new SQLException("Failed to connect");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void closeConnection() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public ResultSet retrieveTables() throws SQLException {
        return metadata.getTables(connection.getCatalog(), schema, "%", new String[]{"TABLE"});
    }

    public ResultSet retrieveTableColumns(String tableName) throws SQLException {
        return metadata.getColumns(connection.getCatalog(), schema, tableName, "%");

    }

    public ResultSet retrieveTablePKs(String tableName) throws SQLException {
        return metadata.getPrimaryKeys(connection.getCatalog(), schema, tableName);
    }

    public ResultSet retrieveTableFKs(String tableName) throws SQLException {
        return metadata.getImportedKeys(connection.getCatalog(), schema, tableName);
    }

    public String getSchemaName() {
        return schema;
    }

    public Connection getConnection() {
        return connection;
    }


    @Override
    public Table retrieveDataFromTable(String tableName) {
        return runQuery("SELECT * FROM " + tableName + ";");
    }

    @Override
    public boolean isJoin(String srcTable, String fkCol,
                        String tgtTable, String pkCol) {
        /* Example address_typed.person_key point to person_typed.person_key
                SELECT DISTINCT address_typed.person_key
                FROM address_typed
                WHERE address_typed.person_key IS NOT NULL
                AND NOT EXISTS (
                    SELECT 1
                    FROM person_typed
                    WHERE person_typed.person_key = address_typed.person_key
                )
         */
        String query = String.format(
                "SELECT DISTINCT %s.%s FROM %s \n" +
                "WHERE %s.%s IS NOT NULL \n" +
                "AND NOT EXISTS ( \n" +
                "   SELECT 1 FROM %s \n" +
                "   WHERE %s.%s = %s.%s\n); \n",
                srcTable, fkCol, srcTable,
                srcTable, fkCol,
                tgtTable,
                tgtTable, pkCol, srcTable, fkCol
        );
        return runQuery(query).rowCount() == 0;
    }

    @Override
    public Table selectRowsWithValue(String tableName, String column, String value) {
        String query = String.format(
                "SELECT * FROM %s WHERE %s = %s ;",
                            tableName, column, value
        );
        return runQuery(query);
    }


    public Table runQuery(String query) {
        Table table;
        try (Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(query)) {
                table = Table.create();
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();

                // Create columns for the table
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    int columnType = metaData.getColumnType(i);
                    ColumnType tablesawColumnType = type.get(columnType);
                    Column<?> column = tablesawColumnType.create(columnName);
                    table.addColumns(column);
                }

                // Add rows to the table
                while (resultSet.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        Object value = resultSet.getObject(i);
                        if (value instanceof LocalDateTime) {
                            LocalDateTime localDateTime = (LocalDateTime) value;
                            java.util.Date date = java.sql.Timestamp.valueOf(localDateTime);
                            table.column(i - 1).appendObj(date);
                        } else {
                            table.column(i - 1).appendObj(value);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return table;
    }


    private static HashMap<Integer, ColumnType> initializeMap() {
        return new HashMap<>(
                new ImmutableMap.Builder<Integer, ColumnType>()
                        .put(Types.BOOLEAN, ColumnType.BOOLEAN)
                        .put(Types.BIT, ColumnType.BOOLEAN)
                        .put(Types.DECIMAL, ColumnType.DOUBLE)
                        .put(Types.DOUBLE, ColumnType.DOUBLE)
                        .put(Types.FLOAT, ColumnType.DOUBLE)
                        .put(Types.NUMERIC, ColumnType.DOUBLE)
                        .put(Types.REAL, ColumnType.FLOAT)
                        // Instant, LocalDateTime, OffsetDateTime and ZonedDateTime are often mapped to
                        // timestamp
                        .put(Types.TIMESTAMP, ColumnType.INSTANT)
                        .put(Types.INTEGER, ColumnType.INTEGER)
                        .put(Types.DATE, ColumnType.LOCAL_DATE)
                        .put(Types.TIME, ColumnType.LOCAL_TIME)
                        .put(Types.BIGINT, ColumnType.LONG)
                        .put(Types.SMALLINT, ColumnType.SHORT)
                        .put(Types.TINYINT, ColumnType.SHORT)
                        .put(Types.BINARY, ColumnType.STRING)
                        .put(Types.CHAR, ColumnType.STRING)
                        .put(Types.NCHAR, ColumnType.STRING)
                        .put(Types.NVARCHAR, ColumnType.STRING)
                        .put(Types.VARCHAR, ColumnType.STRING)
                        .put(Types.LONGVARCHAR, ColumnType.STRING)
                        .put(Types.LONGNVARCHAR, ColumnType.STRING)
                        .build());
    }


}


















