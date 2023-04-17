package org.example.database_connector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class RTable {
    public static class FKpointer {
        public String refTable;
        public String refColumn;
        public FKpointer(String refTable, String refColumn) {
            this.refTable = refTable;
            this.refColumn = refColumn;
        }
    }

    private HashMap<String, String> columns;
    private HashSet<String> PKs;
    private HashMap<String, FKpointer> FKs;
    private HashSet<String> PK_FK_intersection;
    private boolean hasSimpleAttribute;

    public RTable() {
        columns = new HashMap<>();
        PKs = new HashSet<>();
        FKs = new HashMap<>();
        hasSimpleAttribute = false;
    }
    public void setTableOperations() {
        set_PK_FK_intersection();
        setHasSimpleAttribute();
    }

    public void addColumn(String name, String datatype) {
        columns.put(name, datatype);
    }
    public int nColumns() {
        return columns.size();
    }
    //------------------------------------------------------------------------
    // PRIMARY KEYS
    //------------------------------------------------------------------------
    public void addPK(String columnName) {
        PKs.add(columnName);
    }
    public HashSet<String> getPKs() {
        return PKs;
    }
    public int nPk() {
        return PKs.size();
    }
    public boolean isPK(String columnName) {
        return PKs.contains(columnName);
    }
    //------------------------------------------------------------------------
    // FOREIGN KEYS
    //------------------------------------------------------------------------
    public void addFK(String columnName, String refTable, String refColumn) {
        FKs.put(columnName, new FKpointer(refTable, refColumn));
    }
    public HashMap<String, FKpointer> getFKs() {
        return FKs;
    }
    public FKpointer getFKpointer(String fkCol) {
        return FKs.get(fkCol);
    }
    //------------------------------------------------------------------------
    // PRIMARY/FOREIGN KEY COMMON ELEMENTS
    //------------------------------------------------------------------------

    private void set_PK_FK_intersection(){
        PK_FK_intersection = new HashSet<>(PKs);
        PK_FK_intersection.retainAll(FKs.keySet());
    }
    public Set<String> getIntersection(){
        return PK_FK_intersection;
    }
    public boolean is_PKs_eq_FKs() {
        return FKs.keySet().equals(PKs);
    }
    public boolean is_PKs_subset_FKs(){
        return FKs.keySet().containsAll(PKs);
    }

    private void setHasSimpleAttribute() {
        for (String col : columns.keySet()) {
            if (!PKs.contains(col) && !FKs.containsKey(col)) {
                hasSimpleAttribute = true;
                return;
        }}
    }
    public boolean hasSimpleAttribute(){
        return hasSimpleAttribute;
    }
}
