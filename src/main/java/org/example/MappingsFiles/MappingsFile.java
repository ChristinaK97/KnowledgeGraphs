package org.example.MappingsFiles;

import com.google.gson.Gson;
import org.example.InputPoint.SQLdb.DBSchema;
import org.example.POextractor.Properties;
import org.example.POextractor.RulesetApplication;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.MappingsFiles.MappingsFileTemplate.Column;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;
import org.example.util.JsonUtil;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.example.util.Util.PO2DO_Mappings;

public class MappingsFile {

    private HashMap<String, ArrayList<Transformation>> trf = new HashMap<>();
    private MappingsFileTemplate fileTemplate;
    private String msBasePrefix;
    private boolean isRDB;

    public MappingsFile() {
        fileTemplate = new MappingsFileTemplate();
    }

    public void extractMappingsFile(Object dataSource, String msBasePrefix, RulesetApplication rs) {
        this.msBasePrefix = msBasePrefix;
        this.isRDB = dataSource instanceof DBSchema;
        gatherTrfs(rs);

        if (isRDB)
            createJSON((DBSchema) dataSource);
        else {
            addProperties(rs.getFKObjProperties().getProperties(), "ObjectProperty");
            createJSON(rs.getRootElementName(), rs.getClasses().get("Table"));
        }
        saveMappingsFile();
    }

    public static List<Table> readMapJSON() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(PO2DO_Mappings)) {
            // Convert JSON file to Java object
            return gson.fromJson(reader, MappingsFileTemplate.class).getTables();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }


    public void saveMappingsFile(List<Table> tablesList) {
        fileTemplate.setTables(tablesList);
        saveMappingsFile();
    }
    public void saveMappingsFile() {
        JsonUtil.saveToJSONFile(PO2DO_Mappings, fileTemplate);
    }


    // =====================================================================================================
    class Transformation {
        String ontoElement;
        String type;
        public Transformation(String ontoElement, String type) {
            this.ontoElement = ontoElement;
            this.type = type;
        }
        @Override
        public String toString() {
            return ontoElement;
        }
    }

    private void gatherTrfs(RulesetApplication rs) {
        rs.getClasses().forEach((_type, classes) -> {
            classes.forEach((elName, elClass) -> {
                addTrf(elName, elClass, "Class");
        });});
        try{
            addProperties(rs.getAttrObjProp().getProperties(), "ObjectProperty");
        }catch (NullPointerException e) {
            // turnAttrToClasses was false
        }
        addProperties(rs.getDataProperties().getProperties(), "DataProperty");
    }

    private void addProperties(HashMap<String, Properties.DomRan> prop, String type) {
        prop.forEach((propName, domRan) -> {
            for(String field : domRan.getExtractedField()) {
                /*if(!isRDB)
                    field = field.substring(field.lastIndexOf('/') + 1);*/
                addTrf(field, propName, type);
            }
        });
    }

    private void addTrf(String elementName, String ontoElement, String type) {

        ontoElement = msBasePrefix + ontoElement;
        if (trf.containsKey(elementName))
            trf.get(elementName).add(new Transformation(ontoElement, type));
        else
            trf.put(elementName, new ArrayList<>(Collections.singleton(new Transformation(ontoElement, type))));
    }

// =====================================================================================================

    private void createJSON(DBSchema db) {

        ArrayList<String> tables = new ArrayList<>(db.getrTables().keySet());
        Collections.sort(tables);

        for(String tableName : tables) {

            if (trf.containsKey(tableName)) {
                Table table = new Table(tableName);

                Mapping tableMapping = new Mapping(
                        trf.get(tableName).get(0).type,
                        trf.get(tableName).get(0).ontoElement,
                        "", true, null);
                table.setMapping(tableMapping);

                // Table Columns =========================================================================================
                for (String columnName : db.getrTables().get(tableName).getColumns().keySet()) {
                    String t_c = String.format("%s.%s", tableName, columnName);

                    if (trf.containsKey(t_c)) {
                        Column column = new Column(columnName);

                        for (Transformation t : trf.get(t_c))
                            column.addMapping(new Mapping(
                                    t.type, t.ontoElement, "", true, null)
                            );
                        table.addColumn(column);
                    }
                }
                fileTemplate.addTable(table);
            }
        }
    }

// =====================================================================================================

    private void createJSON(String rootElementName, HashMap<String, String> tableClasses) {
        rootElementName = "/" + rootElementName;
        HashMap<String, Table> tableClassesTable = new HashMap<>();
        for(String classField: tableClasses.keySet()) {
            Table table = new Table(classField);
            Mapping tableMapping = new Mapping(
                    trf.get(classField).get(0).type,
                    trf.get(classField).get(0).ontoElement,
                    "", true, null);
            table.setMapping(tableMapping);
            tableClassesTable.put(classField, table);
            fileTemplate.addTable(table);
        }

        System.out.println(tableClassesTable.keySet());

        ArrayList<String> fieldsNames = new ArrayList<>(trf.keySet());
        Collections.sort(fieldsNames);

        for(String elName : fieldsNames) {
            if(elName.equals(rootElementName))
                continue;

            Column field = new Column(elName);
            for (Transformation t : trf.get(elName))
                field.addMapping(new Mapping(
                        t.type, t.ontoElement, "", true, null)
                );
            String fieldTable = elName.substring(0, elName.lastIndexOf("/"));
            System.out.println(elName + " " + fieldTable);
            tableClassesTable.get(fieldTable).addColumn(field);
        }
    }

// =====================================================================================================



}
