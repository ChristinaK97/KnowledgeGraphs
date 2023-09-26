package org.example.InputPoint.SQLdb;

import java.util.*;

public class RTable {
    public static class FKpointer {
        public String refTable;
        public String refColumn;
        public FKpointer(String refTable, String refColumn) {
            this.refTable = refTable;
            this.refColumn = refColumn;
        }

        @Override
        public String toString() {
            return String.format("%s.%s", refTable, refColumn);
        }
    }

    private HashMap<String, String> columns;
    private HashSet<String> PKs;
    private HashMap<String, FKpointer> FKs;
    private HashSet<String> PK_FK_intersection;
    private HashSet<String> FK_PK_difference;
    private boolean hasSimpleAttribute;
    private boolean isPK_subsetOf_FK;

    public RTable() {
        columns = new HashMap<>();
        PKs = new HashSet<>();
        FKs = new HashMap<>();
        hasSimpleAttribute = false;
    }
    public void setTableOperations() {
        set_PK_FK_intersection();
        setHasSimpleAttribute();
        set_isPK_subsetOf_FK();
        set_FK_PK_difference();
    }
    //------------------------------------------------------------------------
    // PRIMARY COLUMNS
    //------------------------------------------------------------------------
    public void addColumn(String name, String datatype) {
        columns.put(name, datatype);
    }
    public int nColumns() {
        return columns.size();
    }

    public HashMap<String, String> getColumns() {
        return columns;
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

    public boolean isFK(String columnName) {
        return FKs.containsKey(columnName);
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

    private void set_FK_PK_difference() {
        FK_PK_difference = new HashSet<>();
        FKs.forEach((fk, fkp) -> {
            if(!PKs.contains(fk))
                FK_PK_difference.add(fkp.toString());
        });
    }
    public HashSet<String> FK_PK_difference() {
        return FK_PK_difference;
    }

    public boolean is_PKs_eq_FKs() {
        return FKs.keySet().equals(PKs);
    }
    private void set_isPK_subsetOf_FK(){
        isPK_subsetOf_FK = FKs.keySet().containsAll(PKs);
    }
    public boolean is_PKs_subset_FKs(){
        return isPK_subsetOf_FK;
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
    public String getColumnSQLtype(String columnName) {
        return columns.get(columnName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        columns.forEach((colName, datatype) -> {
           sb.append("\t\t").append(colName).append("\t").append(datatype);
           if (isPK(colName))
               sb.append("\tPK");
           if (isFK(colName))
               sb.append("\tFK ").append(FKs.get(colName).toString());
           sb.append("\n");
        });
        sb.append("----------------------------");
        return sb.toString();
    }
}
