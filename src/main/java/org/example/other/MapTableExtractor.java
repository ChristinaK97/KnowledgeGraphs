package org.example.other;

import com.google.gson.Gson;

import java.io.FileReader;
import java.util.List;
import org.example.other.JSONFormatClasses.Table;
import org.example.other.JSONFormatClasses.Column;
import org.example.other.JSONFormatClasses.Mapping;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.io.csv.CsvWriteOptions;
import tech.tablesaw.io.csv.CsvWriter;

public class MapTableExtractor {

    private List<Table> tablesMaps;
    StringColumn tableCol = StringColumn.create("Table");
    StringColumn columnCol = StringColumn.create("Column");
    StringColumn matchCol = StringColumn.create("Match");

    public MapTableExtractor() {
        readMapJSON();
        parseTables();
    }

    private void readMapJSON() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader("EFS_mappings.json")) {
            // Convert JSON file to Java object
            tablesMaps = gson.fromJson(reader, JSONFormatClasses.class).getTables();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void parseTables() {
        for(Table tableMaps : tablesMaps) {
            tableCol.append(tableMaps.getTable());
            columnCol.append("");

            if(tableMaps.getMapping().hasMatch())
                matchCol.append(tableMaps.getMapping().getMatchResource());
            else
                matchCol.append(tableMaps.getMapping().getOntoElResource());

            p(tableMaps.getTable());
            p(tableMaps.getMapping().getMatchResource());

            for(Column col : tableMaps.getColumns()) {
                tableCol.append("");
                columnCol.append(col.getColumn());

                p(col.getColumn());
                parseColumnMapping(col);
                System.out.println();
            }
            System.out.println("======");
            tableCol.append("");
            columnCol.append("");
            matchCol.append("");
        }
        tech.tablesaw.api.Table df = tech.tablesaw.api.Table.create("MyDataFrame", tableCol, columnCol, matchCol);
        CsvWriteOptions options = CsvWriteOptions.builder("TableWithMappings.csv")
                .header(true)
                .build();
        new CsvWriter().write(df, options);

        System.out.println(df);
    }

    private void parseColumnMapping(Column col) {
        Mapping objMap   = col.getObjectPropMapping();
        Mapping classMap = col.getClassPropMapping();
        Mapping dataMap  = col.getDataPropMapping();

        StringBuilder bl = new StringBuilder();

        if(objMap.getPathURIs() != null) {

            List<String> path = objMap.getPathResources();
            if(Character.isUpperCase(path.get(0).charAt(0)))
                bl.append(getPropertyNodeString(objMap.getOntoElResource()));

            for (String pathNode : path)
                if (Character.isLowerCase(pathNode.charAt(0)))
                    bl.append(getPropertyNodeString(pathNode));
                else
                    bl.append(getClassNodeString(pathNode));
        }

        if(objMap.hasMatch())
            bl.append(getPropertyNodeString(objMap.getMatchResource()));
        else if (classMap.hasMatch())
            bl.append(getPropertyNodeString(objMap.getOntoElResource()));


        if(classMap.hasMatch())
            bl.append(getClassNodeString(classMap.getMatchResource()));


        if(dataMap.getPathURIs() != null) {

            List<String> path = dataMap.getPathResources();
            if(Character.isUpperCase(path.get(0).charAt(0)))
                bl.append(getPropertyNodeString(dataMap.getOntoElResource()));

            for (String pathNode : path)
                if (Character.isLowerCase(pathNode.charAt(0)))
                    bl.append(getPropertyNodeString(pathNode));
                else
                    bl.append(getClassNodeString(pathNode));
        }

        if(dataMap.hasMatch())
            bl.append(getPropertyNodeString(dataMap.getMatchResource()));
        else if (classMap.hasMatch())
            bl.append(getPropertyNodeString(dataMap.getOntoElResource()));


        if(! (objMap.hasMatch() || classMap.hasMatch() || dataMap.hasMatch()))
            bl.append(getPropertyNodeString(dataMap.getOntoElResource()));


        bl.append(getClassNodeString("VALUE"));

        System.out.println(bl);
        matchCol.append(bl.toString());
    }

    private String getPropertyNodeString(String nodeString) {
        return String.format(" -(%s)-> ", nodeString);
    }
    private String getClassNodeString(String nodeString) {
        return String.format("[%s]", nodeString);
    }

    private void p(Object o) {
        System.out.println(o.toString());
    }

    public static void main(String[] args) {
        new MapTableExtractor();
    }
}
