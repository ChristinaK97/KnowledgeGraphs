package org.example.other;

import com.google.gson.Gson;

import java.io.FileReader;
import java.util.List;
import org.example.other.JSONFormatClasses.Table;
import org.example.other.JSONFormatClasses.Column;
import org.example.other.JSONFormatClasses.Mapping;

public class MapTableExtractor {

    private List<Table> tablesMaps;

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
            p(tableMaps.getTable());
            p(tableMaps.getMapping().getMatchResource());

            for(Column col : tableMaps.getColumns()) {
                p(col.getColumn());
                parseColumnMapping(col);
                System.out.println();
            }
        }
        System.out.println("======");
    }

    private void parseColumnMapping(Column col) {
        Mapping objMap   = col.getObjectPropMapping();
        Mapping classMap = col.getClassPropMapping();
        Mapping dataMap  = col.getDataPropMapping();

        StringBuilder bl = new StringBuilder();

        if(objMap.getPathURIs() != null)
            for(String pathNode : objMap.getPathResources())
                if (Character.isLowerCase(pathNode.charAt(0)))
                    bl.append(getPropertyNodeString(pathNode));
                else
                    bl.append(getClassNodeString(pathNode));

        if(objMap.hasMatch())
            bl.append(getPropertyNodeString(objMap.getMatchResource()));

        if(classMap.hasMatch())
            bl.append(getClassNodeString(classMap.getMatchResource()));

        if(dataMap.getPathURIs() != null)
            for(String pathNode : dataMap.getPathResources())
                if (Character.isLowerCase(pathNode.charAt(0)))
                    bl.append(getPropertyNodeString(pathNode));
                else
                    bl.append(getClassNodeString(pathNode));

        if(dataMap.hasMatch())
            bl.append(getPropertyNodeString(dataMap.getMatchResource()));

        bl.append(getClassNodeString("VALUE"));

        System.out.println(bl);
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
