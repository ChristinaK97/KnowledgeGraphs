package org.example.InputPoint.SQLdb.SQLconnector;

import com.github.jsonldjava.shaded.com.google.common.collect.ImmutableMap;
import org.example.InputPoint.SQLdb.SQLconnector.Credentials;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;

public class DatabaseConnector {

    private String schema;
    private Connection connection;
    private DatabaseMetaData metadata;
    private static HashMap<Integer, ColumnType> type = initializeMap();

    public DatabaseConnector() {
        connect();
    }

    private void connect() {
        try {
            Credentials cred = new Credentials();
            String url = cred.getUrl();
            this.schema = url.substring(url.lastIndexOf('/') + 1);
            connection = DriverManager.getConnection(url, cred.getUser(), cred.getPassword());
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


    public Table retrieveDataFromTable(String tableName) {
        return runQuery("SELECT * FROM " + tableName + ";");
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


















