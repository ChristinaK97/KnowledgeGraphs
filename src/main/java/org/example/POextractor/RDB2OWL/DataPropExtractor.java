package org.example.POextractor.RDB2OWL;

import org.example.InputPoint.SQLdb.DBSchema;
import org.example.InputPoint.SQLdb.RTable;
import org.example.POextractor.Properties;

import java.util.HashMap;

import static org.example.util.Annotations.*;

public class DataPropExtractor {

    private HashMap<String, String> tableClasses;

    private Properties dataProperties = new Properties();
    private Properties attrObjProperties = new Properties();

    private HashMap<String, String> attrClasses = new HashMap<>();
    private boolean turnAttrToClasses;



    public DataPropExtractor(DBSchema db, boolean turnAttrToClasses, HashMap<String, String> tableClasses) {
        this.turnAttrToClasses = turnAttrToClasses;
        this.tableClasses = tableClasses;

        db.getrTables().forEach((tableName, table) -> {
            if(tableClasses.containsKey(tableName))
                extractDataProp(tableName, tableClasses.get(tableName), table);
        });
        System.out.println(dataProperties);
    }

    private void extractDataProp(String tableName, String tClass, RTable table) {
        table.getColumns().forEach((colName, datatype) -> {
            if (!(table.isPK(colName) || table.isFK(colName))) {

                String extractedField = String.format("%s.%s", tableName, colName);

                // Column has the same name as a table class
                if(tableClasses.containsValue(colName) || tableClasses.containsValue(colName.toLowerCase()))
                    colName = duplicateAttrClassName(colName);

                // Class (existing)     Column new class
                // tableName -has_colName-> colName -has_colName_value-> datatype
                if(turnAttrToClasses) {
                    attrClasses.put(extractedField, colName);
                    attrObjProperties.addProperty("dp", tClass,
                                                      attrObjectPropertyName(colName),
                                                      colName, extractedField);

                    dataProperties.addProperty("dp", colName,
                                                    dataPropName(colName),
                                                    SQL2XSD(datatype), extractedField);

                }else
                    // Don't turn attributes to classes
                    dataProperties.addProperty("dp", tClass,
                                                    directDataPropertyName(colName),
                                                    SQL2XSD(datatype), extractedField);
        }});
    }

    public HashMap<String, String> getAttrClasses() {return attrClasses;}
    public Properties getAttrObjProperties() {return attrObjProperties;}
    public Properties getDataProperties() {return dataProperties;}


    private String SQL2XSD(String sqlType) {
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
            case "string":
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











