package org.example.B_InputDatasetProcessing.Tabular;

import static org.example.A_Coordinator.Pipeline.config;
import static org.example.util.FileHandler.getFileNameWithoutExtension;
import static org.example.util.XSDmappers.fixDateFormat;

import org.example.util.FileHandler;
import org.example.util.Pair;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;
import tech.tablesaw.io.csv.CsvReadOptions;
// import tech.tablesaw.io.xlsx.XlsxReadOptions;
// import tech.tablesaw.io.xlsx.XlsxReader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TabularFilesReader {

    boolean log = false;

    private RelationalDB db;

    public static HashSet<String> nullValues = new HashSet<>(Set.of("none", "null", "", " ", "-"));
    private static final String EMPTY = "";
    private static final String UNKNOWN = "-";
    private static final String UNKNOWN_HEADER = "Unknown_Header_";
    private static final String PKCol = "PKCol";
    private static final long FIRST_ROW_ID = 0;

// ---------------------------------------------------------------------------------------------------

    public TabularFilesReader(List<String> files) {
        db = new RelationalDB();
        for(String downloadedFilePath : files) {
            String singleTableName = files.size()==1 ? config.Out.DefaultRootClassName : null;
            addTable(singleTableName, downloadedFilePath);
        }                                                                                                               if(log) System.out.println("FINISHED READING TABLES");
    }

    public RelationalDB getRelationalDB() {
        System.out.println(db.toString());
        return db;
    }


    public void addTable(String tableName, String downloadedFilePath) {
        String processedFilePath = FileHandler.getProcessedFilePath(downloadedFilePath,
                "csv", true);
        if(tableName == null)
            tableName = getFileNameWithoutExtension(processedFilePath);

        Table table;                                                                                                    if(log) System.out.println("TABLE " + tableName);
        HashMap<String, String> colTypes;
        if(!Files.exists(Paths.get(processedFilePath))) {                                                               if(log)System.out.println("PROCESS " + downloadedFilePath);
            Pair<List<List<String>>, Integer> rowsInfo = readRows(downloadedFilePath);
            List<String> headers = repairHeaders(rowsInfo.el1(), rowsInfo.el2());                                       if(log) System.out.println("HEADERS = " + headers);

            table = turnToTablesaw(rowsInfo.el1(), headers);                                                            if(log) System.out.println(table.first(5));
            dropEmptyUnknownColumns(table);                                                                             if(log) System.out.println(table.first(5));
            addPKCol(table);

            // column types
            Pair<Table, HashMap<String,String>> tableTyped = determineColumnTypes(table);
            table = tableTyped.el1();                                                                                   if(log) {System.out.println(table.first(10)); for(String c:table.columnNames()) System.out.println(c + " " + tableTyped.el2().get(c)); }
            colTypes = tableTyped.el2();

            saveProcessedFile(table, processedFilePath);
        } else {                                                                                                        if(log) System.out.println("LOAD " + processedFilePath);
            table = readCSV(processedFilePath);
            colTypes = determineColumnTypes(table).el2();
        }

        db.addTable(tableName, table, colTypes, PKCol);                                                                  if(log) System.out.println("===============================================================\n");
    }



    private Pair<List<List<String>>, Integer> readRows(String downloadedFilePath) {
        // Read the input CSV and calculate the max number of cells
        List<List<String>> rows = new ArrayList<>();
        String delimiter = null;
        int maxCells = 0;
        try {
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(downloadedFilePath));
            while ((line = reader.readLine()) != null) {
                if(delimiter == null)
                    delimiter = detectDelimiter(line);
                List<String> row = new ArrayList<>(List.of(line.split(delimiter)));
                maxCells = Math.max(maxCells, row.size());
                rows.add(row);
            }
            reader.close();
        }catch (IOException ignored) {}
        return new Pair<>(rows, maxCells);
    }


    private String detectDelimiter(String line) {

        Matcher matcher = Pattern.compile("[^_a-zA-Z0-9]").matcher(line);
        HashMap<String, Integer> counts = new HashMap<>();
        String maxSymbol = null;
        int maxCount = Integer.MIN_VALUE;
        while (matcher.find()) {
            String symbol = matcher.group();
            int symbolCount = counts.getOrDefault(symbol,0) + 1;
            counts.put(symbol, symbolCount);
            if(symbolCount > maxCount) {
                maxCount = symbolCount;
                maxSymbol = symbol;
            }
        }
        return maxSymbol;
    }


    private List<String> repairHeaders(List<List<String>> rows, int maxCells) {
        List<String> headers = rows.get(0);

        //Duplicate headers
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

        // Add missing cells and headers to each row
        for (int i = 1 ; i < rows.size() ; ++i) {
            while (rows.get(i).size() < maxCells) {
                rows.get(i).add(EMPTY);
            }
        }
        return headers;
    }


    private Table turnToTablesaw(List<List<String>> rows, List<String> headers) {
        List<StringColumn> columns = new ArrayList<>(headers.size());
        for(String header : headers)
            columns.add(StringColumn.create(header));
        for(int r=1; r < rows.size(); ++r) {
            List<String> row = rows.get(r);
            for(int h=0; h<row.size(); ++h)
                columns.get(h).append(row.get(h));
        }
        Table table = Table.create();
        for(StringColumn column : columns)
            table.addColumns(column);
        return table;
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


// =====================================================================================================

    private Pair<Table,HashMap<String, String>> determineColumnTypes(Table table) {

        HashMap<String, String> colTypes  = new HashMap<>(table.columnCount());
        ArrayList<Column<?>> typedColumns = new ArrayList<>(table.columnCount());

        for(String colName : table.columnNames()) {
            if(log) System.out.println(">> COLUMN : " + colName);

            StringColumn col = table.stringColumn(colName).map(String::toLowerCase);
            Set<String> cUn = col.unique().asSet();
            boolean hasMissingValues = cUn.removeAll(nullValues);

            // boolean ------------------------------------------------------------------------------
            boolean isBool =
                    (cUn.size()==1 && ((cUn.contains("0") || cUn.contains("false"))
                                    || (cUn.contains("1") || cUn.contains("true")))) ||
                    (cUn.size()==2 && ((cUn.contains("0") || cUn.contains("false"))
                                    && (cUn.contains("1") || cUn.contains("true"))));
            if(isBool) {
                colTypes.put(colName, "boolean");
                if(!hasMissingValues) {
                    String trueValue = cUn.contains("true") || cUn.contains("false") ? "true" : "1";
                    BooleanColumn boolCol = BooleanColumn.create(colName);
                    col.forEach(value -> boolCol.append(value.equals(trueValue)));
                    typedColumns.add(boolCol);
                }else
                    typedColumns.add(col);
            }
            if(isBool) continue;
            // ------------------------------------------------------------------------------
            int nInt = 0, nDouble = 0, nDate = 0;

            String dateP1 = "\\d{4}[./-]\\d{1,2}";
            String dateP2 = "\\d{1,2}[./-]\\d{4}";
            String dateP3 = "\\d{4}";
            String dateP4 = "\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}";
            String dateP5 = "\\d{1,2}[./-]\\d{1,2}[./-]\\d{4}";

            ArrayList<String> dateFormats = new ArrayList<>();
            for(String val : col) {
                if(nullValues.contains(val)) {
                    ++ nDate;
                    dateFormats.add(null);
                    continue;
                }

                // date ------------------------------------------------------------------
                boolean matchedDateP1 = val.matches(dateP1),
                        matchedDateP2 = val.matches(dateP2),
                        matchedDateP3 = val.matches(dateP3),
                        matchedDateP4 = val.matches(dateP4),
                        matchedDateP5 = val.matches(dateP5);

                String delim = val.contains(".") ? "\\." : val.contains("-") ? "-" : "/";
                String[] parts = val.split(delim);
                String originalFormat = null;

                if(matchedDateP1 || matchedDateP2) {
                    int yearIndex  = matchedDateP1 ? 0 : 1;
                    int monthIndex = matchedDateP1 ? 1 : 0;
                    int year  = Integer.parseInt(parts[yearIndex]);
                    int month = Integer.parseInt(parts[monthIndex]);
                    if(month>=1 && month<=12 && year>=1900 && year<=2200) {
                        originalFormat = matchedDateP1 ? String.format("yyyy%sMM", delim) : String.format("MM%syyyy",delim);
                }}
                else if(matchedDateP3) {
                    int year = Integer.parseInt(val);
                    if(year>=1900 && year<=2500) {
                        originalFormat = "yyyy";
                }}
                else if(matchedDateP4) {
                    int year = Integer.parseInt(parts[0]);
                    int par1 = Integer.parseInt(parts[1]);
                    int par2 = Integer.parseInt(parts[2]);
                    if(year>=1900 && year<=2500) {
                        if(par1>=1 && par1<=12 && par2>=1 && par2<=31)
                            originalFormat = String.format("yyyy%sMM%sdd", delim, delim);
                        else if(par2>=1 && par2<=12 && par1>=1 && par1<=31)
                            originalFormat = String.format("yyyy%sdd%sMM", delim, delim);
                }}
                else if(matchedDateP5) {
                    int year = Integer.parseInt(parts[2]);
                    int par1 = Integer.parseInt(parts[0]);
                    int par2 = Integer.parseInt(parts[1]);
                    if(year>=1900 && year<=2500) {
                        if(par1>=1 && par1<=12 && par2>=1 && par2<=31)
                            originalFormat = String.format("MM%sdd%syyyy", delim, delim);
                        else if(par2>=1 && par2<=12 && par1>=1 && par1<=31)
                            originalFormat = String.format("dd%sMM%syyyy", delim, delim);
                }}
                if(originalFormat != null){
                    ++nDate;
                    dateFormats.add(originalFormat);
                }else {
                    // numeric ------------------------------------------------------------------
                    try {
                        Integer.parseInt(val);
                        ++nInt;
                        continue;
                    }catch (NumberFormatException ignore){}
                    try {
                        Double.parseDouble(val.replace(",","."));
                        ++nDouble;
                    }catch (NumberFormatException ignore){}
                }
            }
            // -------------------------------------------------------------------------------
            Set<String> uniqueDateFormats = new HashSet<>(dateFormats);
            uniqueDateFormats.remove(null);                                                                                                         if(log){if(uniqueDateFormats.size() > 0) {System.out.println(colName);System.out.println(col.first(10).asList());dateFormats.forEach(f -> System.out.print(f!=null ? f + ", ":""));System.out.println("\n");}System.out.println(colName +"\tInt = " + nInt + "\tnDouble = " + nDouble + "\tnUniqueDateFormats = " + uniqueDateFormats.size());}

            if(nDate == col.size() && uniqueDateFormats.size() > 0) {
                StringColumn dateCol = StringColumn.create(colName);
                for(int i=0; i<col.size(); ++i)
                    dateCol.append(nullValues.contains(col.get(i)) ? null : fixDateFormat(col.get(i), dateFormats.get(i)));
                typedColumns.add(dateCol);
                colTypes.put(colName, "timestamp");

            }else if (uniqueDateFormats.size() > 0 && nDate > nInt && nDate > nDouble) {                                                              if(log) System.out.println(uniqueDateFormats);
                typedColumns.add(col);
                colTypes.put(colName, "varchar");
            }else {
                try {
                    if(nDouble > 0) {
                        DoubleColumn doubleCol = DoubleColumn.create(colName);
                        col.forEach(value -> doubleCol.append(nullValues.contains(value) ? null :
                                             Double.parseDouble(value.replace(",","."))));
                        typedColumns.add(doubleCol);
                        colTypes.put(colName, "double");
                    } else {
                        IntColumn intCol = IntColumn.create(colName);
                        col.forEach(value -> intCol.append(nullValues.contains(value) ? null : Integer.parseInt(value)));
                        typedColumns.add(intCol);
                        colTypes.put(colName, "int");

                    }
                }catch (NumberFormatException e) {
                    typedColumns.add(col);
                    colTypes.put(colName, "varchar");
                }
            }
        }
        Table typedTable = Table.create();
        for(Column<?> col : typedColumns)
            typedTable.addColumns(col);
        return new Pair<>(typedTable, colTypes);
    }

// =====================================================================================================

    private void addPKCol(Table table) {
        // Create a new IntColumn for row identifiers
        StringColumn rowIdColumn = StringColumn.create(PKCol, table.rowCount());
        for (int i = 0; i < table.rowCount(); i++)
            rowIdColumn.set(i, String.valueOf(FIRST_ROW_ID + i + 1));
        // Add the new column to the table
        table.insertColumn(table.columnCount(), rowIdColumn);
    }

    private void determineCandidateKeys(Table table) {
        for(String colName : table.columnNames()) {
            Set<String> unique = table.stringColumn(colName).asSet();
            unique.removeAll(nullValues);
            if(unique.size() == table.rowCount())
                System.out.println("Candidate key  =  " + colName);
        }
    }


// =====================================================================================================

    private Table readCSV(String filePath) {
        CsvReadOptions options = CsvReadOptions.builder(filePath)
                .header(true)
                .columnTypesToDetect(List.of(ColumnType.STRING))
                .build();
        return Table.read().csv(options);
    }

    private void saveProcessedFile(Table table, String processedFilePath) {
        table.write().csv(processedFilePath);
        System.out.println("CSV repaired successfully.");
    }




}


