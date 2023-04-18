package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;
import org.example.database_connector.RTable.FKpointer;
import org.example.database_connector.RTable;
import org.example.ontology_extractor.OntologyExtractor.Property;

import java.util.*;

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
            objPropRule3_4();
            objPropRule6();
            objPropRule7();
            objPropRule8();
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

    private void objPropRule3_4() {
        if (table.is_PKs_subset_FKs()) {
            Collection<FKpointer> fks = table.getFKs().values();
            for (FKpointer fkp1 : fks) {
                for (FKpointer fkp2 : fks) {
                    if(isClass(fkp1.refTable) && isClass(fkp2.refTable) && !fkp1.refTable.equals(fkp2.refTable))
                        objProperties.add(new Property("r3", fkp1.refTable, fkp2.refTable));
                    if(isClass(tableName)) {
                        objProperties.add(new Property("r4", fkp1.refTable, tableName));
                        objProperties.add(new Property("r4", fkp2.refTable, tableName));
                    }
        }}}
    }

    private void objPropRule6() {
        HashSet<String> referencesTables = new HashSet<>();
        if(table.nPk() % 2 == 0 && table.is_PKs_subset_FKs())
            for (String PFkey : table.getIntersection())
                referencesTables.add(table.getFKpointer(PFkey).refTable);
        if(referencesTables.size() == 1) {
            String selfRefTable = referencesTables.iterator().next();
            objProperties.add(new Property("r6", selfRefTable, selfRefTable));
        }
    }

    private void objPropRule7() {
        if(isClass(tableName))
            for(FKpointer fkp : table.getFKs().values())
                if(tableName.equals(fkp.refTable) && table.isPK(fkp.refColumn))
                    objProperties.add(new Property("r7", tableName, tableName));
    }

    private void objPropRule8() {
        db.getrTables().forEach((tableName2, table2) -> {
            if(!tableName.equals(tableName2)) {
                HashSet<String> FKintersection = new HashSet<>(table.getFKs().keySet());
                FKintersection.retainAll(table2.getFKs().keySet());
                if(FKintersection.size() > 0)
                    objProperties.add(new Property("r", tableName, tableName2));
            }
        });
    }


    private boolean isClass(String tableName) {
        return true;
    }
}







