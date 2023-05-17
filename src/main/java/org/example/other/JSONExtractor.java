package org.example.other;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.example.database_connector.DBSchema;
import org.example.database_connector.RTable;
import org.example.ontology_extractor.Properties;
import org.example.ontology_extractor.Properties.DomRan;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class JSONExtractor {

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
    HashMap<String, ArrayList<Transformation>> trf = new HashMap<>();
    String msBbasePrefix;

    public void createMappingJSON_fromOntology(DBSchema db,
                                               String msBbasePrefix,
                                               HashMap<String, String> tableCls,
                                               HashMap<String, String> attrCls,
                                               Properties objProp,
                                               Properties newObjProp,
                                               Properties dataProp) {
        this.msBbasePrefix = msBbasePrefix;
        tableCls.forEach((tableName, tableClass) -> {
            addTrf(tableName, tableClass, "Class");
        });
        attrCls.forEach((attName, attClass) -> {
            addTrf(attName, attClass, "Class");
        });
        addProperties(newObjProp.getProperties(), "ObjectProperty");
        addProperties(dataProp.getProperties(), "DataProperty");


        trf.forEach((el, tr) -> {
            System.out.println(el + " " + tr);
        });


        // Create JSON ==================================================================

        ArrayList<String> tables = new ArrayList<>(db.getrTables().keySet());
        Collections.sort(tables);

        JsonObject json = new JsonObject();
        JsonArray tablesArray = new JsonArray();

        for (String tableName : tables) {

            if (trf.containsKey(tableName)) {
                JsonObject tableObject = new JsonObject();
                tableObject.addProperty("table", tableName);

                JsonObject tableMapping = new JsonObject();

                tableMapping.addProperty("type", trf.get(tableName).get(0).type);
                tableMapping.addProperty("ontoEl", trf.get(tableName).get(0).ontoElement);
                tableMapping.addProperty("match", "");
                tableMapping.addProperty("isCons", "");

                tableObject.add("mapping", tableMapping);

                JsonArray columnsArray = new JsonArray();

                // Table Columns =========================================================================================
                for (String column : db.getrTables().get(tableName).getColumns().keySet()) {
                    String t_c = String.format("%s.%s", tableName, column);

                    if (trf.containsKey(t_c)) {
                        JsonArray colMappings = new JsonArray();
                        for (Transformation t : trf.get(t_c)) {
                            JsonObject colMapping = new JsonObject();
                            colMapping.addProperty("type", t.type);
                            colMapping.addProperty("ontoEl", t.ontoElement);
                            colMapping.addProperty("match", "");
                            colMapping.addProperty("isCons", "");
                            colMappings.add(colMapping);
                        }
                        JsonObject columnObj = new JsonObject();
                        columnObj.addProperty("column", column);
                        columnObj.add("mappings", colMappings);
                        columnsArray.add(columnObj);
                    }
                }
                tableObject.add("columns", columnsArray);

                // FKs =========================================================================================
                JsonArray fkArray = new JsonArray();
                RTable table = db.getTable(tableName);
                table.getFKs().forEach((fkCol, fkp) -> {
                    JsonObject fkObj = new JsonObject();
                    fkObj.addProperty("fkCol", fkCol);
                    fkObj.addProperty("ref", String.format("%s.%s", fkp.refTable, fkp.refColumn));

                    String sourceClass = tableCls.get(tableName);
                    String targetClass = tableCls.get(fkp.refTable);
                    String objP = String.format("p_%s_%s", sourceClass, targetClass);
                    String invP = String.format("p_%s_%s", targetClass, sourceClass);

                    String ontoEl = objProp.contains(objP) ? msBbasePrefix + objP : (objProp.contains(invP) ? msBbasePrefix + invP : "-");

                    JsonObject fkMapping = new JsonObject();
                    fkMapping.addProperty("type", "ObjectProperty");
                    fkMapping.addProperty("ontoEl", ontoEl);
                    fkMapping.addProperty("match", "");
                    fkMapping.addProperty("isCons", "");
                    fkObj.add("mapping", fkMapping);

                    fkArray.add(fkObj);
                });
                tableObject.add("FKs", fkArray);
                // =========================================================================================

                tablesArray.add(tableObject);
            }
        }
        json.add("tables", tablesArray);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonString = gson.toJson(json);

        //System.out.println(jsonString);
        File file = new File("EFS_mappings.json");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(jsonString);
            writer.close();
        }catch (IOException e) {
            e.printStackTrace();
        }

    }



    private void addProperties(HashMap<String, DomRan> prop, String type) {
        prop.forEach((propName, domRan) -> {
            for(String field : domRan.getExtractedField())
                addTrf(field, propName, type);
        });
    }

    private void addTrf(String elementName, String ontoElement, String type) {

        ontoElement = msBbasePrefix + ontoElement;
        if (trf.containsKey(elementName))
            trf.get(elementName).add(new Transformation(ontoElement, type));
        else
            trf.put(elementName, new ArrayList<>(Collections.singleton(new Transformation(ontoElement, type))));
    }
// ===================================================================================================================

    public void createMappingJSON_forFKobjectProperties(DBSchema db,
                                                        String msBbasePrefix,
                                                        HashMap<String, String> tableCls,
                                                        Properties objProp) {
        this.msBbasePrefix = msBbasePrefix;
        JsonObject json = new JsonObject();
        JsonArray objPropArray = new JsonArray();

        HashMap<String, JsonObject> propertyObject = new HashMap<>();
        ArrayList<String> sortedProperties = new ArrayList<>(objProp.getProperties().keySet());
        Collections.sort(sortedProperties);

        for(String objP : sortedProperties) {

            JsonObject objPropObject = new JsonObject();
            objPropObject.addProperty("objProp", objP);
            objPropObject.addProperty("isInverseP", objProp.contains(objProp.getPropertyDomRan(objP).getInverse()));

            JsonObject objPMapping = new JsonObject();

            objPMapping.addProperty("type", "ObjectProperty");
            objPMapping.addProperty("ontoEl", msBbasePrefix + objP);
            objPMapping.addProperty("match", "");
            objPMapping.addProperty("isCons", "");

            objPropObject.add("mapping", objPMapping);
            propertyObject.put(objP, objPropObject);
        }

        db.getrTables().forEach((tableName, table) -> {
            table.getFKs().forEach((fkCol, fkp) -> {

                String sourceClass = tableCls.get(tableName);
                String targetClass = tableCls.get(fkp.refTable);
                String objP  = String.format("p_%s_%s", sourceClass, targetClass);
                String invP  = String.format("p_%s_%s", targetClass, sourceClass);
                String loopP = String.format("has_%s", sourceClass);

                for (String p : new String[]{objP, invP, loopP})
                    if (propertyObject.containsKey(p)) {
                        propertyObject.get(p).addProperty("fKeyColumn", String.format("%s.%s", tableName, fkCol));
                        propertyObject.get(p).addProperty("references", String.format("%s.%s", fkp.refTable, fkp.refColumn));
                    }
        });});

        for(String objP : sortedProperties)
            objPropArray.add(propertyObject.get(objP));
        json.add("objProps", objPropArray);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonString = gson.toJson(json);

        //System.out.println(jsonString);
        File file = new File("EFS_mappings_ObjProp.json");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(jsonString);
            writer.close();
        }catch (IOException e) {
            e.printStackTrace();
        }
    }



// ===================================================================================================================
    public void createMappingJSON_fromDB (DBSchema db) throws IOException {

        ArrayList<String> tables = new ArrayList<>(db.getrTables().keySet());
        Collections.sort(tables);

        JsonObject json = new JsonObject();
        JsonArray tablesArray = new JsonArray();

        for (String table : tables) {
            JsonObject tableObject = new JsonObject();
            tableObject.addProperty("table", table);

            JsonObject mappingObject = new JsonObject();
            mappingObject.addProperty("match", "");
            mappingObject.addProperty("type", "");
            tableObject.add("mapping", mappingObject);

            JsonArray columnsArray = new JsonArray();
            for (String column : db.getrTables().get(table).getColumns().keySet()) {
                JsonObject columnObject = new JsonObject();
                columnObject.addProperty("column", column);

                JsonArray pathArray = new JsonArray();
                pathArray.add("");
                pathArray.add("");
                columnObject.add("path", pathArray);

                columnObject.addProperty("match", "");
                columnObject.addProperty("type", "");

                columnsArray.add(columnObject);
            }
            tableObject.add("columns", columnsArray);

            tablesArray.add(tableObject);
        }

        json.add("tables", tablesArray);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonString = gson.toJson(json);

        System.out.println(jsonString);
        File file = new File("EFS_mappings.json");
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(jsonString);
        writer.close();
    }


}
