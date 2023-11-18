package org.example.C_POextractor.RDB2OWL;

import static org.example.A_Coordinator.Pipeline.config;
import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.B_InputDatasetProcessing.Tabular.RTable;
import org.example.C_POextractor.Properties;
import org.example.util.XSDmappers;

import java.util.HashMap;

import static org.example.A_Coordinator.config.Config.DEV_MODE;
import static org.example.util.Annotations.*;

public class DataPropExtractor {

    private HashMap<String, String> tableClasses;

    private Properties dataProperties = new Properties();
    private Properties attrObjProperties = new Properties();

    private HashMap<String, String> attrClasses = new HashMap<>();



    public DataPropExtractor(RelationalDB db, HashMap<String, String> tableClasses) {
        this.tableClasses = tableClasses;

        db.getrTables().forEach((tableName, table) -> {
            if(tableClasses.containsKey(tableName))
                extractDataProp(tableName, tableClasses.get(tableName), table);
        });                                                                                                             if(DEV_MODE) System.out.println("Data Properties:\n" + dataProperties);
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
                if(config.Out.turnAttributesToClasses) {
                    attrClasses.put(extractedField, colName);
                    attrObjProperties.addProperty("dp", tClass,
                                                      attrObjectPropertyName(colName),
                                                      colName, extractedField);

                    dataProperties.addProperty("dp", colName,
                                                    dataPropName(colName),
                                                    XSDmappers.SQL2XSD(datatype), extractedField);

                }else
                    // Don't turn attributes to classes
                    dataProperties.addProperty("dp", tClass,
                                                    directDataPropertyName(colName),
                                                    XSDmappers.SQL2XSD(datatype), extractedField);
        }});
    }

    public HashMap<String, String> getAttrClasses() {return attrClasses;}
    public Properties getAttrObjProperties() {return attrObjProperties;}
    public Properties getDataProperties() {return dataProperties;}


}

