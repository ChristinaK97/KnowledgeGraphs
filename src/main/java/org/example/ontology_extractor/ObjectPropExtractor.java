package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;
import org.example.database_connector.RTable.FKpointer;
import org.example.database_connector.RTable;

import java.util.*;

public class ObjectPropExtractor {

    private Properties objProperties = new Properties();
    private HashMap<String, String> convertedIntoClass;
    private String tableName;
    private RTable table;

    public ObjectPropExtractor(DBSchema db, HashMap<String, String> convertedIntoClass) {
        this.convertedIntoClass = convertedIntoClass;
        db.getrTables().forEach((tableName, table) -> {
            this.tableName = tableName;
            this.table = table;
            objPropRule1(db);
            objPropRule2();
            objPropRule3_4();
            objPropRule6();
            objPropRule7();
            objPropRule8(db);
        });
        System.out.println(objProperties);
    }

    private void objPropRule1(DBSchema db) {

        table.getFKs().forEach((fkCol, fkp) -> {
            String thisClass = tClass(tableName);
            String otherClass = tClass(fkp.refTable);

            if(isClass(thisClass) && isClass(otherClass) && !thisClass.equals(otherClass) &&
                    !table.isPK(fkCol) && db.isPK(fkp.refTable, fkp.refColumn))

                objProperties.addProperty("r1", thisClass, null, otherClass);
        });
    }

    private void objPropRule2() {
        String thisClass = tClass(tableName);
        if( isClass(thisClass) &&
            table.nPk() > 1 && table.getIntersection().size() > 0 &&
            table.nColumns() > table.nPk())
        {
            for(String key : table.getIntersection()) {
                String otherClass = tClass(table.getFKpointer(key).refTable);

                if (isClass(otherClass) && !thisClass.equals(otherClass))
                    objProperties.addProperty("r2", thisClass, null, otherClass);
        }}
    }

    private void objPropRule3_4() {
        if (table.is_PKs_subset_FKs()) {
            Collection<FKpointer> fks = table.getFKs().values();

            for (FKpointer fkp1 : fks) {
                String otherClass1 = tClass(fkp1.refTable);
                if(isClass(otherClass1)) {

                    for (FKpointer fkp2 : fks) {
                        String otherClass2 = tClass(fkp2.refTable);

                        if (isClass(otherClass2) && !otherClass1.equals(otherClass2))
                            objProperties.addProperty("r3", otherClass1, null, otherClass2);

                        String thisClass = tClass(tableName);
                        if (isClass(thisClass)) {
                            objProperties.addProperty("r4", otherClass1, null, thisClass);
                            objProperties.addProperty("r4", otherClass2, null, thisClass);
                        }
                }}
        }}
    }

    private void objPropRule6() {
        // self ref property
        HashSet<String> referencesTables = new HashSet<>();
        if(table.nPk() % 2 == 0 && table.is_PKs_subset_FKs())
            for (String PFkey : table.getIntersection())
                referencesTables.add(
                        tClass(table.getFKpointer(PFkey).refTable)
                );

        if(referencesTables.size() == 1) {
            String selfRefClass = referencesTables.iterator().next();
            if(isClass(selfRefClass))
                objProperties.addProperty("r6", selfRefClass, "has_"+selfRefClass, selfRefClass);
        }
    }

    private void objPropRule7() {
        // self ref property
        String selfRefClass = tClass(tableName);
        if(isClass(selfRefClass))
            for(FKpointer fkp : table.getFKs().values())

                if(tableName.equals(fkp.refTable) && table.isPK(fkp.refColumn))
                    objProperties.addProperty("r7", selfRefClass, "has_"+selfRefClass, selfRefClass);
    }

    private void objPropRule8(DBSchema db) {
        String thisClass = tClass(tableName);
        if (isClass(thisClass))

            db.getrTables().forEach((tableName2, table2) -> {
                String otherClass = tClass(tableName2);
                if(isClass(otherClass) && !thisClass.equals(otherClass)) {

                    HashSet<String> FKintersection = new HashSet<>(table.getFKs().keySet());
                    FKintersection.retainAll(table2.getFKs().keySet());

                    if(FKintersection.size() > 0)
                        objProperties.addProperty("r8", thisClass, null, otherClass);
                }
            });
    }

    private String tClass (String tableName){
        return convertedIntoClass.get(tableName);
    }
    private boolean isClass(String tClass) {
        return tClass != null;
    }


    public Properties getObjProperties() {
        return objProperties;
    }


}







