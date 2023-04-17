package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;
import org.example.database_connector.RTable.FKpointer;
import org.example.database_connector.RTable;
import org.example.ontology_extractor.OntologyExtractor.Property;

import java.util.ArrayList;

public class ObjectPropExtractor {

    private ArrayList<Property> objProperties = new ArrayList<>();
    private DBSchema db;
    private String tableName;
    private RTable table;

    public ObjectPropExtractor(DBSchema db) {
        this.db = db;
        db.getrTables().forEach((tableName, table) -> {
            this.tableName = tableName;
            this.table = table;
            objPropRule1();
            objPropRule2();
        });
        System.out.println(objProperties);
    }

    private void objPropRule1() {

        table.getFKs().forEach((fkCol, fkp) -> {
            if(isClass(tableName) && !table.isPK(fkCol) &&
               isClass(fkp.refTable) && db.isPK(fkp.refTable, fkp.refColumn)
            )
                objProperties.add(new Property("r1", tableName, fkp.refTable));
        });
    }

    private void objPropRule2() {

        if( isClass(tableName) && table.nPk() > 1 && table.getIntersection().size() > 0 &&
            table.nColumns() > table.nPk())
        {
            for(String key : table.getIntersection()) {
                FKpointer fkp = table.getFKpointer(key);
                if (isClass(fkp.refTable))
                    objProperties.add(new Property("r2", tableName, fkp.refTable));
        }}
    }


    private boolean isClass(String tableName) {
        return true;
    }
}







