package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;
import org.example.database_connector.RTable;

import java.util.HashMap;

public class DataPropExtractor {

    private HashMap<String, String> convertedIntoClass;

    private Properties dataProp = new Properties();
    private Properties newObjProp = new Properties();

    private HashMap<String, String> attrClasses = new HashMap<>();
    private boolean turnAttrToClasses;



    public DataPropExtractor(DBSchema db, boolean turnAttrToClasses, HashMap<String, String> convertedIntoClass) {
        this.turnAttrToClasses = turnAttrToClasses;
        this.convertedIntoClass =  convertedIntoClass;

        db.getrTables().forEach((tableName, table) -> {
            if(convertedIntoClass.containsKey(tableName))
                extractDataProp(tableName, convertedIntoClass.get(tableName), table);
        });
        System.out.println(dataProp);
    }

    private void extractDataProp(String tableName, String tClass, RTable table) {
        table.getColumns().forEach((colName, datatype) -> {
            if (!(table.isPK(colName) || table.isFK(colName))) {

                String extractedField = String.format("%s.%s", tableName, colName);
                colName = colName.toLowerCase();

                // Column has the same name as a table class
                if(convertedIntoClass.containsValue(colName))
                    colName = colName + "_ATTR";

                // Class (existing)     Column new class
                // tableName -has_colName-> colName -has_colName_value-> datatype
                if(turnAttrToClasses) {
                    attrClasses.put(extractedField, colName);
                    newObjProp.addProperty("dp", tClass,"has_"+colName,  colName, extractedField);
                    dataProp.addProperty("dp", colName,"has_"+colName+"_VALUE",  convertToXSD(datatype), extractedField);

                }else
                    // Don't turn attributes to classes
                    dataProp.addProperty("dp", tClass,"has_"+colName,  convertToXSD(datatype), extractedField);
        }});
    }

    public HashMap<String, String> getAttrClasses() {return attrClasses;}
    public Properties getNewObjProp() {return newObjProp;}
    public Properties getDataProp() {return dataProp;}


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











