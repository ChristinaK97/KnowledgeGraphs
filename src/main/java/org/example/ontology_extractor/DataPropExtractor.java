package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;
import org.example.database_connector.RTable;

import java.util.ArrayList;
import java.util.HashMap;

public class DataPropExtractor {

    private Properties dataProp = new Properties();
    private Properties newObjProp = new Properties();

    private ArrayList<String> attrClasses = new ArrayList<>();
    private boolean turnAttrToClasses;

    public DataPropExtractor(DBSchema db, boolean turnAttrToClasses, HashMap<String, String> convertedIntoClass) {
        this.turnAttrToClasses = turnAttrToClasses;
        db.getrTables().forEach((tableName, table) -> {
            if(convertedIntoClass.containsKey(tableName))
                extractDataProp(convertedIntoClass.get(tableName), table);
        });
        System.out.println(dataProp);
    }

    private void extractDataProp(String tClass, RTable table) {
        table.getColumns().forEach((colName, datatype) -> {
            if (!(table.isPK(colName) || table.isFK(colName))) {
                colName = colName.toLowerCase();

                // Class (existing)     Column new class
                // tableName -has_colName-> colName -has_colName_value-> datatype
                if(turnAttrToClasses) {
                    attrClasses.add(colName);
                    newObjProp.addProperty("dp", tClass,"has_"+colName,  colName);
                    dataProp.addProperty("dp", colName,"has_"+colName+"_value",  convertToXSD(datatype));

                }else
                    dataProp.addProperty("dp", tClass,"has_"+colName,  convertToXSD(datatype));
        }});
    }

    public ArrayList<String> getAttrClasses() {return attrClasses;}
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











