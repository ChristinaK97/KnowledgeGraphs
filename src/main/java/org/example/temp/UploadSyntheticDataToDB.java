package org.example.temp;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.B_InputDatasetProcessing.Tabular.RTable;
import org.example.B_InputDatasetProcessing.Tabular.SQLConnector;
import org.example.util.XSDmappers;

import java.io.*;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.sql.Date;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UploadSyntheticDataToDB {

    String folderPath = "src/main/resources/Use Case/Fintech/EPIBANK/Downloaded Data/simulated_data - sample";
    String ddl = "src/main/resources/Use Case/Fintech/EPIBANK/Other/EPIBANK_SQL_DDL_MySQL _without fks.sql";

    SQLConnector connector;
    String schema;
    List<String> tableOrder;
    HashSet<String> missingTables = new HashSet<>();
    HashMap<String, HashSet<Integer>> toNext;
    RelationalDB db;

    public UploadSyntheticDataToDB() {
        connector =  new SQLConnector();
        schema = connector.getSchemaName();
        db = new RelationalDB();

        tableOrder = extractTableOrderFromFile(ddl);
        gatherMissingTables();
        toNext = gatherReferencesToNextTables();

        System.out.println(tableOrder);
        System.out.println(toNext);

        uploadCSVs();
        updateWithToNextColumns();
        connector.closeConnection();
    }

    public static List<String> extractTableOrderFromFile(String filePath) {
        List<String> tableNames = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            StringBuilder createStatement = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                createStatement.append(line);

                if (line.trim().endsWith(";")) {
                    String tableName = extractTableNameFromCreateStatement(createStatement.toString());
                    if (tableName != null)
                        tableNames.add(tableName);
                    createStatement.setLength(0);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return tableNames;
    }
    public static String extractTableNameFromCreateStatement(String createStatement) {
        // Regular expression pattern to match the table name
        Pattern pattern = Pattern.compile("create\\s+table\\s+if\\s+not\\s+exists\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(createStatement);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }


    private void gatherMissingTables() {
        for (String tableName : tableOrder)
            if(!new File(folderPath + tableName + ".csv").exists())
                missingTables.add(tableName);
    }




    private HashMap<String, HashSet<Integer>> gatherReferencesToNextTables() {
        HashMap<String, HashSet<Integer>> toNext = new HashMap<>();
        for(String tableName : tableOrder) {
            CSVParser csvParser;
            try {
                String filePath = folderPath + tableName + ".csv";
                csvParser = new CSVParser(new FileReader(filePath), CSVFormat.DEFAULT.withHeader());
            } catch (IOException e) {
                continue;
            }
            toNext.put(tableName, new HashSet<>());
            for (int i = 0; i < csvParser.getHeaderNames().size(); i++) {

                String header = csvParser.getHeaderNames().get(i);

                if(db.getTable(tableName).isFK(header)) {
                    String refTable = db.getTable(tableName).getFKpointer(header).refTable;
                    if (tableOrder.indexOf(refTable) > tableOrder.indexOf(tableName))
                        toNext.get(tableName).add(i);
                }
            }
        }
        return toNext;
    }

    private HashSet<Integer> gatherReferencesToMissingTables(String tableName, CSVParser csvParser) {
        HashSet<Integer> toDrop = new HashSet<>();
        for (int i = 0; i < csvParser.getHeaderNames().size(); i++) {
            String header = csvParser.getHeaderNames().get(i);
            if(db.getTable(tableName).isFK(header)) {
                String refTable = db.getTable(tableName).getFKpointer(header).refTable;
                if (missingTables.contains(refTable))
                    toDrop.add(i);
            }
        }
        return toDrop;
    }

    /////////////////////////////////////////////////////////////////////////////////
    private void uploadCSVs() {

        for (String tableName : tableOrder) {
            try {
                System.out.println("TABLE " + tableName);
                String filePath = folderPath + tableName + ".csv";
                Reader reader = new FileReader(filePath);
                CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader());
                HashSet<Integer> toDrop = gatherReferencesToMissingTables(tableName, csvParser);
                uploadCSVtoTable(tableName, csvParser, toDrop);
            }catch (IOException e) {
                System.out.println("No datafile for table " + tableName);
            }
        }
    }

    private void uploadCSVtoTable(String tableName, CSVParser csvParser, HashSet<Integer> toDrop) {
        List<String> headers = csvParser.getHeaderNames();
        String insertQuery = generateInsertQuery(tableName, headers, toDrop);
        RTable rTable = db.getTable(tableName);

        try (PreparedStatement statement = connector.getConnection().prepareStatement(insertQuery)) {

            for (CSVRecord csvRecord : csvParser) {
                int skipped = 0;
                for (int i = 0; i < headers.size(); i++) {

                    if(toDrop.contains(i) || toNext.get(tableName).contains(i)) {
                        skipped ++;
                        continue;
                    }
                    String columnHeader = headers.get(i);
                    String columnValue = csvRecord.get(columnHeader);
                    String columnDatatype = rTable.getColumnSQLtype(columnHeader);
                    //System.out.printf("%s %s %s %s\n", tableName, columnHeader, columnDatatype, columnValue);
                    addValue(statement, i + 1 - skipped, columnValue, columnDatatype);
                }
                System.out.println(statement.toString());
                statement.executeUpdate();
            }
            System.out.println("Data uploaded successfully.");
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

    }

    private void addValue(PreparedStatement statement, int i, String columnValue, String columnDatatype) {
        if(columnValue.equals("")) {
            //System.out.println("FOUND NULL");
            try {
                statement.setNull(i, determineSqlType(columnDatatype));
                return;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            if("DATETIME".equals(columnDatatype)) {
                String format = "";
                if(Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher(columnValue).matches())
                    format = "yyyy-MM-dd";
                else if(Pattern.compile("\\d{2}/\\d{2}/\\d{4}").matcher(columnValue).matches())
                    format = "dd/MM/yyyy";
                else if(Pattern.compile("\\d{4}.\\d{1,2}").matcher(columnValue).matches()) {
                    columnValue = XSDmappers.fixDateFormat(columnValue, "yyyy.MM");
                    format = "yyyy-MM-dd";
                }else if(Pattern.compile("\\d{4}").matcher(columnValue).matches()) {
                    columnValue = XSDmappers.fixDateFormat(columnValue, "YYYY");
                    format = "yyyy-MM-dd";
                }
                SimpleDateFormat inputDateFormat = new SimpleDateFormat(format) ;
                Date dateValue = new Date(inputDateFormat.parse(columnValue).getTime());
                statement.setDate(i, dateValue);
            }else if("INT".equals(columnDatatype)) {
                int value = Integer.parseInt(columnValue);
                statement.setInt(i, value);
            }else if("FLOAT".equals(columnDatatype)) {
                float value = Float.parseFloat(columnValue);
                statement.setFloat(i, value);
            }else{
                statement.setString(i, columnValue);
            }

        }catch (ParseException | NumberFormatException | SQLException e) {
            try { // string
                statement.setString(i, columnValue);
            }catch (SQLException e2) {e2.printStackTrace();}
        }

    }

    private int determineSqlType(String columnDatatype) {
        switch (columnDatatype) {
            case "VARCHAR":
                return Types.VARCHAR;
            case "INT":
                return Types.INTEGER;
            case "BIGINT":
                return Types.BIGINT;
            case "FLOAT":
                return Types.FLOAT;
            case "BIT" :
                return Types.BIT;
            case "BINARY":
                return Types.BINARY;
            case "BOOLEAN":
                return Types.BOOLEAN;
            case "DATETIME":
                return Types.DATE;
            default:
                // Handle unknown data types or provide a default SQL type
                return Types.OTHER; // You can change this to a more appropriate default
        }
    }

    private String generateInsertQuery(String tableName, List<String> columnHeaders, HashSet<Integer> toDrop) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("INSERT INTO ").append(tableName).append(" (");
        for (int i = 0; i < columnHeaders.size(); i++) {

            if(toDrop.contains(i) || toNext.get(tableName).contains(i))
                continue;

            queryBuilder.append(columnHeaders.get(i));
            if (i < columnHeaders.size() - 1- toNext.get(tableName).size()) {
                queryBuilder.append(", ");
            }
        }
        queryBuilder.append(") VALUES (");
        for (int i = 0; i < columnHeaders.size(); i++) {
            if(toDrop.contains(i) || toNext.get(tableName).contains(i))
                continue;
            queryBuilder.append("?");
            if (i < columnHeaders.size() - 1 - toNext.get(tableName).size()) {
                queryBuilder.append(", ");
            }
        }
        queryBuilder.append(")");

        return queryBuilder.toString();
    }

    /////////////////////////////////////////////////////////////////////////////////

    private void updateWithToNextColumns() {

        toNext.forEach((tableName, nextIndexSet) -> {
            if(nextIndexSet.size() > 0) {
                RTable rTable = db.getTable(tableName);
                try {
                    System.out.println(tableName);
                    HashSet<String> PKs = db.getTable(tableName).getPKs();
                    String filePath = folderPath + tableName + ".csv";
                    CSVParser csvParser = new CSVParser(new FileReader(filePath), CSVFormat.DEFAULT.withHeader());
                    String updateQuery = buildUpdateQuery(tableName, csvParser, PKs, nextIndexSet);
                    System.out.println(updateQuery);
                    PreparedStatement statement = connector.getConnection().prepareStatement(updateQuery);

                    for (CSVRecord record : csvParser) {
                        int i = 1;
                        for (Integer updateColumn : nextIndexSet) {
                            String headerName = csvParser.getHeaderMap().entrySet().stream()
                                    .filter(entry -> Objects.equals(entry.getValue(), updateColumn))
                                    .map(Map.Entry::getKey)
                                    .findFirst()
                                    .orElse(null);
                            addValue(statement, i++, record.get(updateColumn), rTable.getColumnSQLtype(headerName));
                        }


                        for (String pk : PKs)
                            addValue(statement, i++, record.get(pk), rTable.getColumnSQLtype(pk));
                        System.out.println(statement);
                        statement.executeUpdate();
                    }

                    System.out.println("Data updated successfully.");


                }catch (SQLException | IOException e) {
                    e.printStackTrace();
                }
            }
        });


    }

    private static String buildUpdateQuery(String tableName,  CSVParser csvParser, HashSet<String> PKs, HashSet<Integer> nextIndexSet) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("UPDATE ").append(tableName).append(" SET ");

        for (Integer updateColumn : nextIndexSet) {
            queryBuilder.append(csvParser.getHeaderNames().get(updateColumn)).append(" = ?, ");
        }
        queryBuilder.setLength(queryBuilder.length() - 2);

        queryBuilder.append(" WHERE ");
        for (String primaryKeyColumn : PKs) {
            queryBuilder.append(primaryKeyColumn).append(" = ? AND ");
        }
        queryBuilder.setLength(queryBuilder.length() - 5);

        return queryBuilder.toString();
    }




    public static void main(String[] args) {
        new UploadSyntheticDataToDB();
    }

}
