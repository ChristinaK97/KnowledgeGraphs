package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;
import org.example.database_connector.RTable;

import java.util.ArrayList;
import java.util.HashMap;

public class DataPropExtractor {

    private ArrayList<Property> dataProperties = new ArrayList<>();
    private ArrayList<Property> newObjProp = new ArrayList<>();
    private ArrayList<String> newClasses = new ArrayList<>();
    private boolean turnAttrToClasses;

    public DataPropExtractor(DBSchema db, boolean turnAttrToClasses, HashMap<String, String> convertedIntoClass) {
        this.turnAttrToClasses = turnAttrToClasses;
        db.getrTables().forEach((tableName, table) -> {
            if(convertedIntoClass.containsKey(tableName))
                extractDataProp(convertedIntoClass.get(tableName), table);
        });
        System.out.println(dataProperties);
    }

    private void extractDataProp(String tableClass, RTable table) {
        table.getColumns().forEach((colName, datatype) -> {
            if (!(table.isPK(colName) || table.isFK(colName))) {
                // Class (existing)     Column new class
                // tableName -has_colName-> colName -has_colName_value-> datatype
                colName = colName.toLowerCase();
                if(turnAttrToClasses) {
                    newClasses.add(colName);
                    newObjProp.add(new Property("has_"+colName, tableClass, colName));
                    dataProperties.add(new Property("has_"+colName+"_value", colName, convertToXSD(datatype)));
                }else
                    dataProperties.add(new Property("has_"+colName, tableClass, convertToXSD(datatype)));
        }});
    }

    public ArrayList<String> getNewClasses() {return newClasses;}
    public ArrayList<Property> getNewObjProp() {return newObjProp;}
    public ArrayList<Property> getDataProperties() {return dataProperties;}


    private String convertToXSD(String sqlType) {
        switch (sqlType.toLowerCase()) {
            case "int":
            case "integer":
            case "tinyint":
            case "smallint":
            case "mediumint":
            case "bigint":
                return "xsd:integer";
            case "float":
            case "double":
            case "decimal":
            case "numeric":
                return "xsd:decimal";
            case "date":
                return "xsd:date";
            case "time":
                return "xsd:time";
            case "datetime":
            case "timestamp":
                return "xsd:dateTime";
            case "year":
                return "xsd:gYear";
            case "char":
            case "varchar":
            case "text":
            case "tinytext":
            case "mediumtext":
            case "longtext":
                return "xsd:string";
            case "binary":
            case "varbinary":
            case "blob":
            case "tinyblob":
            case "mediumblob":
            case "longblob":
                return "xsd:base64Binary";
            case "boolean":
            case "bit":
                return "xsd:boolean";
            default:
                return "unknown";
            }
    }


}











