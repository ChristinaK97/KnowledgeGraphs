package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;
import org.example.database_connector.RTable;
import org.example.ontology_extractor.Property;

import java.util.ArrayList;
import java.util.HashSet;

public class DataPropExtractor {

    private HashSet<Property> dataProperties = new HashSet<>();
    private ArrayList<String> newClasses = new ArrayList<>();
    private boolean turnAttrToClasses;

    public DataPropExtractor(DBSchema db, boolean turnAttrToClasses) {
        this.turnAttrToClasses = turnAttrToClasses;
        db.getrTables().forEach((tableName, table) -> {

        });
    }
    private void extractDataProp(String tableName, RTable table) {
        table.getColumns().forEach((colName, datatype) -> {
            if (!(table.isPK(colName) || table.isFK(colName))) {

            }
        });
    }
}











