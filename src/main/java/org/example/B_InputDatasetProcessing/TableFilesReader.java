package org.example.B_InputDatasetProcessing;

import static org.example.A_Coordinator.Runner.config;
import org.example.B_InputDatasetProcessing.SQLdb.DBSchema;
import tech.tablesaw.api.LongColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;
// import tech.tablesaw.io.xlsx.XlsxReadOptions;
// import tech.tablesaw.io.xlsx.XlsxReader;

import java.io.*;
import java.util.*;

public class TableFilesReader {

    private String file;
    DBSchema db;

    private static final String EMPTY = "";
    private static final String UNKNOWN = "-";
    private static String DELIMITER = ";";
    private static final String UNKNOWN_HEADER = "Unknown_Header_";
    private static final String PKCol = "PKCol";
    private static final long FIRST_ROW_ID = 0;

    /** Constructor for reading a single table dataset/file and extracting its schema as a DBSchema with a single table.
     * Call getAsDBSchema() next to retrieve the DBSchema object. */
    public TableFilesReader(List<String> files) {
        db = new DBSchema();
        for(String file : files) {
            this.file = file;
            if(config.In.isCSV())
                addTable();
            else
                throw new UnsupportedOperationException(file.substring(file.lastIndexOf('.')+1) +
                        " is not supported. Import a csv instead.");
        }
    }


    public void addTable() {
        Table table = readCSV();
        dropEmptyUnknownColumns(table);
        String pkCol = addPKCol(table);
        String tableName = file.substring(file.lastIndexOf("/")+1, file.lastIndexOf("."));
        db.addTable(tableName, table, pkCol);
    }

    public Table readCSV() {
        repairCSV();
        System.out.println("Read "+ file);
        // Define custom CsvReadOptions with the desired delimiter
        CsvReadOptions options = CsvReadOptions.builder(file)
                .separator(DELIMITER.charAt(0)) // Set the custom delimiter
                .header(true)                   // Assume the first row contains column names
                .build();
        // Read the CSV file using the specified options
        return Table.read().csv(options);
    }

    private void repairCSV() {

        try {
            List<List<String>> rows = new ArrayList<>();
            int maxCells = 0;

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

            // Read the input CSV and calculate the max number of cells
            while ((line = reader.readLine()) != null) {
                List<String> row = new ArrayList<>(List.of(line.split(DELIMITER)));
                maxCells = Math.max(maxCells, row.size());
                rows.add(row);
            }
            reader.close();
            List<String> headers = rows.get(0);

            //Repair headers
            HashSet<String> headersSet = new HashSet<>(headers.size());
            for(int i = 0 ; i < headers.size() ; ++i) {
                String header = headers.get(i);
                if(headersSet.contains(header)) {
                    header += "_";
                    headers.set(i, header);
                }
                headersSet.add(header);
            }

            // Generate missing headers
            int missingHeaders = maxCells - headers.size();
            for (int i = 0; i < missingHeaders; i++) {
                headers.add(UNKNOWN_HEADER + (headers.size() + i + 1));
            }

            // Add missing cells and headers to each row and write to output CSV
            file = String.format("%s%s.csv", config.In.ProcessedDataDir, file.substring(file.lastIndexOf("/")+1, file.lastIndexOf(".")));
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(String.join(DELIMITER, headers));
            writer.newLine();

            for (int i = 1 ; i < rows.size() ; ++i) {
                while (rows.get(i).size() < maxCells) {
                    rows.get(i).add(EMPTY);
                }
                writer.write(String.join(DELIMITER, rows.get(i)));
                writer.newLine();
            }
            writer.close();
            System.out.println("CSV repaired successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void dropEmptyUnknownColumns(Table table) {
        int rowCount = table.rowCount();
        for (String columnName : table.columnNames()) {
            // int nonEmptyCount = 0;
            boolean hasNonEmpty = false;
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                String cell = table.column(columnName).get(rowIndex).toString();
                if (!cell.equals(EMPTY) && !cell.equals(UNKNOWN)) {
                    hasNonEmpty = true;
                    // ++nonEmptyCount;
                }
            }
            //double percentage = ((double) nonEmptyCount / rowCount) * 100;
            if(!hasNonEmpty && columnName.startsWith(UNKNOWN_HEADER))
                table = table.removeColumns(columnName);
        }
    }

    private String addPKCol(Table table) {
        // Create a new IntColumn for row identifiers
        String pkCol = PKCol;
        LongColumn rowIdColumn = LongColumn.create(pkCol, table.rowCount());
        for (int i = 0; i < table.rowCount(); i++)
            rowIdColumn.set(i, FIRST_ROW_ID + i + 1);
        // Add the new column to the table
        table.insertColumn(table.columnCount(), rowIdColumn);
        return pkCol;
    }


    public DBSchema getAsDBSchema() {
        return db;
    }





    /*TODO public Table readExcel(String file) {
        XlsxReader reader = new XlsxReader();
        XlsxReadOptions options = XlsxReadOptions.builder(file).build();
        Table table = reader.read(options);
        System.out.println(table);
        return table;
    }*/
}
