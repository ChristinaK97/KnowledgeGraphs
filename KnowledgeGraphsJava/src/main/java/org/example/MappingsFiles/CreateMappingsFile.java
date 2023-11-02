package org.example.MappingsFiles;

import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.util.Annotations;
import org.example.C_POextractor.Properties;
import org.example.C_POextractor.RulesetApplication;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.MappingsFiles.MappingsFileTemplate.Column;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import static org.example.A_Coordinator.Pipeline.config;
import static org.example.util.Annotations.CLASS_SUFFIX;
import static org.example.util.Annotations.TABLE_PREFIX;

public class CreateMappingsFile extends ManageMappingsFile {

    private HashMap<String, ArrayList<Transformation>> trf = new HashMap<>();
    private boolean isRDB;

    public void extractMappingsFile(Object dataSource,  RulesetApplication rs) {
        this.isRDB = dataSource instanceof RelationalDB;
        gatherTrfs(rs);

        if (isRDB)
            createJSON((RelationalDB) dataSource);
        else {
            addProperties(rs.getPureObjProperties().getProperties(), "ObjectProperty");
            createJSON(config.Out.RootClassName, rs.getClasses().get(TABLE_PREFIX));
        }
        saveMappingsFile();
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
                addTrf(elName, elClass, CLASS_SUFFIX);
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

        ontoElement = config.Out.POntologyBaseNS + Annotations.rmvInvalidIriChars(ontoElement);
        if (trf.containsKey(elementName))
            trf.get(elementName).add(new Transformation(ontoElement, type));
        else
            trf.put(elementName, new ArrayList<>(Collections.singleton(new Transformation(ontoElement, type))));
    }

// =====================================================================================================

    private void createJSON(RelationalDB db) {

        ArrayList<String> tables = new ArrayList<>(db.getrTables().keySet());
        Collections.sort(tables);

        for(String tableName : tables) {

            if (trf.containsKey(tableName)) {
                Table table = new Table(tableName);

                Mapping tableMapping = new Mapping(
                        trf.get(tableName).get(0).type,
                        trf.get(tableName).get(0).ontoElement,
                        null, null);
                table.setMapping(tableMapping);

                // Table Columns =========================================================================================
                for (String columnName : db.getrTables().get(tableName).getColumns().keySet()) {
                    String t_c = String.format("%s.%s", tableName, columnName);

                    if (trf.containsKey(t_c)) {
                        Column column = new Column(columnName);

                        for (Transformation t : trf.get(t_c))
                            column.addMapping(new MappingsFileTemplate.Mapping(
                                    t.type, t.ontoElement, null, null)
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
        System.out.println(rootElementName);
        HashMap<String, Table> tableClassesTable = new HashMap<>();
        for(String classField: tableClasses.keySet()) {
            Table table = new Table(classField);
            Mapping tableMapping = new Mapping(
                    trf.get(classField).get(0).type,
                    trf.get(classField).get(0).ontoElement,
                    null, null);
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
                        t.type, t.ontoElement, null,null)
                );
            String fieldTable = elName.substring(0, elName.lastIndexOf("/"));
            System.out.println(elName + " " + fieldTable + " " + tableClassesTable.get(fieldTable));
            tableClassesTable.get(fieldTable).addColumn(field);
        }
    }

// =====================================================================================================



}
