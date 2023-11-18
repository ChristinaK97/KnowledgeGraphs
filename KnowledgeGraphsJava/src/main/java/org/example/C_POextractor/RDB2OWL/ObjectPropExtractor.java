package org.example.C_POextractor.RDB2OWL;

import static org.example.A_Coordinator.config.Config.DEV_MODE;
import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.B_InputDatasetProcessing.Tabular.RTable.FKpointer;
import org.example.B_InputDatasetProcessing.Tabular.RTable;
import org.example.util.Annotations;
import org.example.C_POextractor.Properties;

import java.util.*;

public class ObjectPropExtractor {

    private Properties pureObjProperties = new Properties();
    private HashMap<String, String> tableClasses;
    private String tableName;
    private RTable table;

    public ObjectPropExtractor(RelationalDB db, HashMap<String, String> tableClasses) {
        this.tableClasses = tableClasses;
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
        if(DEV_MODE) System.out.println("PURE OBJ PROPS:\n" + pureObjProperties);
    }

    private void objPropRule1(RelationalDB db) {

        table.getFKs().forEach((fkCol, fkp) -> {
            String thisClass = tClass(tableName);
            String otherClass = tClass(fkp.refTable);

            if(isClass(thisClass) && isClass(otherClass) && !thisClass.equals(otherClass) &&
                    !table.isPK(fkCol) && db.isPK(fkp.refTable, fkp.refColumn)) {

                pureObjProperties.addProperty("r1", thisClass, null, otherClass, null);
                pureObjProperties.addProperty("r1 inv", otherClass, null, thisClass, null);
        }});
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
                    pureObjProperties.addProperty("r2", thisClass, null, otherClass, null);
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
                            pureObjProperties.addProperty("r3", otherClass1, null, otherClass2, null);

                        String thisClass = tClass(tableName);
                        if (isClass(thisClass)) {
                            if(!thisClass.equals(otherClass1))
                                pureObjProperties.addProperty("r4", otherClass1, null, thisClass, null);
                            if(!thisClass.equals(otherClass2))
                                pureObjProperties.addProperty("r4", otherClass2, null, thisClass, null);
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
                pureObjProperties.addProperty("r6", selfRefClass,
                                                    Annotations.symmetricObjPropName(selfRefClass),
                                                    selfRefClass, null);
        }
    }

    private void objPropRule7() {
        // self ref property
        String selfRefClass = tClass(tableName);
        if(isClass(selfRefClass))
            for(FKpointer fkp : table.getFKs().values())

                if(tableName.equals(fkp.refTable) && table.isPK(fkp.refColumn))
                    pureObjProperties.addProperty("r7", selfRefClass,
                                                        Annotations.symmetricObjPropName(selfRefClass),
                                                        selfRefClass, null);
    }

    private void objPropRule8(RelationalDB db) {
        String thisClass = tClass(tableName);
        if (isClass(thisClass))

            db.getrTables().forEach((tableName2, table2) -> {
                String otherClass = tClass(tableName2);

                if(isClass(otherClass) && !thisClass.equals(otherClass)) {

                    HashSet<String> FKintersection = new HashSet<>(table.FK_PK_difference());
                    FKintersection.retainAll(table2.FK_PK_difference());

                    if(FKintersection.size() > 0)
                        pureObjProperties.addProperty("r8", thisClass, null, otherClass, null);
                }

            });
    }

    private String tClass (String tableName){
        return tableClasses.get(tableName);
    }
    private boolean isClass(String tClass) {
        return tClass != null;
    }


    public Properties getPureObjProperties() {
        return pureObjProperties;
    }


}







