package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;
import org.example.database_connector.RTable.FKpointer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ClassExtractor {

    DBSchema db;
    Set<String> coveredTables;

    public ClassExtractor(DBSchema db) {
        this.db = db;
        classRule1();
        classRule2();
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
        coveredTables = new HashSet<>(setOfTable.keySet());
        // group tables by the set they belong to
        Map<Integer, List<String>> valueMap = setOfTable.keySet().stream()
                .collect(Collectors.groupingBy(setOfTable::get));
        System.out.println(valueMap.values());
    }

    //-----------------------------------------------------

    private void classRule2() {
        Set<String> newClasses = new HashSet<>();
        db.getrTables().forEach((tableName, table) -> {

            if (!coveredTables.contains(tableName)) {

                if (table.nPk() == 1 ||
                    table.getIntersection().size() >= 1 ||
                    (table.hasSimpleAttribute() && table.is_PKs_subset_FKs())
                )
                    newClasses.add(tableName);
        }});
        System.out.println(newClasses);
        coveredTables.addAll(newClasses);
    }


}










