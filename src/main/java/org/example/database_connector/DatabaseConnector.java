package org.example.database_connector;

import java.sql.*;

public class DatabaseConnector {

    private String schema;
    private Connection connection;
    private DatabaseMetaData metadata;

    public DatabaseConnector() {
        connect();
    }

    private void connect() {
        try {
            Credentials cred = new Credentials();
            String url = cred.getUrl();
            this.schema = url.substring(url.lastIndexOf('/')+1);
            connection = DriverManager.getConnection(url, cred.getUser(), cred.getPassword());
            metadata = connection.getMetaData();

            if (connection == null)
                throw new SQLException("Failed to connect");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void closeConnection(){
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


}


















