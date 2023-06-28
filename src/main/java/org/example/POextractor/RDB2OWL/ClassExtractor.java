package org.example.POextractor.RDB2OWL;

import org.example.InputPoint.SQLdb.DBSchema;
import org.example.InputPoint.SQLdb.RTable.FKpointer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ClassExtractor {

    DBSchema db;
    HashMap<String, String> coveredIntoClass = new HashMap<>();

    public ClassExtractor(DBSchema db) {
        this.db = db;
        classRule1();
        classRule2();
        System.out.println(coveredIntoClass);
    }

    public void classRule1(){
        HashMap<String, Integer> setOfTable = new HashMap<>();
        AtomicInteger nSets = new AtomicInteger(0);

        db.getrTables().forEach((tableName, table) -> {

            if(table.is_PKs_eq_FKs()) {
                for(FKpointer fkp: table.getFKs().values()) {

                    if(setOfTable.containsKey(fkp.refTable))
                        setOfTable.put(tableName, setOfTable.get(fkp.refTable));
                    else {
                        setOfTable.put(tableName, nSets.get());
                        setOfTable.put(fkp.refTable, nSets.getAndIncrement());
                    }
        }}});
        // group tables by the set they belong to
        Map<Integer, List<String>> valueMap = setOfTable.keySet().stream()
                .collect(Collectors.groupingBy(setOfTable::get));
        System.out.println(valueMap.values());
        createClasses(valueMap.values());
    }

    //-----------------------------------------------------

    private void classRule2() {
        db.getrTables().forEach((tableName, table) -> {
            if (!coveredIntoClass.containsKey(tableName)) {

                if (table.nPk() == 1 ||
                    table.getIntersection().size() >= 1 ||
                    (table.hasSimpleAttribute() && table.is_PKs_subset_FKs())
                )
                    createClasses(tableName);
        }});
    }


    private void createClasses(Collection<List<String>> tables) {
        for(List<String> sharedTables : tables) {
            int n = sharedTables.size();

            String s = sharedTables.get(0);
            int len = s.length();

            String commonName = "";

            for (int i = 0; i < len; i++) {
                for (int j = i + 1; j <= len; j++) {

                    String stem = s.substring(i, j);
                    int k = 1;
                    for (k = 1; k < n; k++)
                        if (!sharedTables.get(k).contains(stem))
                            break;
                    if (k == n && commonName.length() < stem.length())
                        commonName = stem;
            }}
            if(commonName.equals(""))
                commonName = String.join("_", sharedTables);
            for (String tableName : sharedTables)
                coveredIntoClass.put(tableName, commonName);
        }
    }

    private void createClasses(String tableName) {
        coveredIntoClass.put(tableName, tableName);
    }

    public HashMap<String, String> getConvertedIntoClass(){
        return coveredIntoClass;
    }
}










