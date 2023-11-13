package org.example.MappingsFiles;

import org.example.util.Ontology;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;


public class MappingsFileTemplate {

    private List<Table> tables;
    //TODO: change the mappings file template to use hashmaps instead of lists
    //TODO: for now, locate table by name using its index. Don't write to gson
    private transient HashMap<String, Integer> tablesIdx;

    public MappingsFileTemplate() {
        this.tables = new ArrayList<>();
        this.tablesIdx = new HashMap<>();
    }

    public void setTables(List<Table> tables) {
        this.tables = tables;
        for (int i = 0; i < tables.size(); i++)
            this.tablesIdx.put(tables.get(i).table, i);
    }
    public void addTable(String tableName, Table table) {
        if(tablesIdx.containsKey(tableName))
            tables.set(tablesIdx.get(tableName), table);
        else{
            tables.add(table);
            tablesIdx.put(table.table, tables.size()-1);
        }
    }

    public List<Table> getTables() {
        return tables;
    }

    /** Returns null if table doesn't exist */
    public Table getTable(String tableName) {
        try {
            return tables.get(tablesIdx.get(tableName));
        }catch (NullPointerException e){
            return null;
        }
    }
    /** Returns null if this column doesn't exist in this table, or if the table doesn't exists */
    public Column getTableColumn(String tableName, String colName) {
        try {
            return getTable(tableName).getColumn(colName);
        }catch (NullPointerException e) {
            return null;
        }
    }

    //================================================================================

    /**
     * Such as a relation table or a CSV file
     */
    public static class Table {
        private String table;
        private ArrayList<String> filenames;
        private ArrayList<String> docIds;
        private Mapping mapping;

        private List<Column> columns;
        private transient HashMap<String, Integer> columnsIdx;

        public Table(String tableName, String filename, String docId) {
            this.table = tableName;
            this.columns = new ArrayList<>();
            this.columnsIdx = new HashMap<>();

            addTableSource(filename, docId);
        }
        public void addTableSource(String filename, String docId){
            if(filenames == null)
                filenames = new ArrayList<>();
            if(filename != null) {
                System.out.printf("\tIn table %s from %s, add new filename = %s\n", table, filenames, filename);
                filenames.add(filename);
            }if(docIds == null)
                docIds = new ArrayList<>();
            if(docId != null)
                docIds.add(docId);
        }

        public String getTable() {
            return table;
        }
        public void setTable(String table) {
            this.table = table;
        }

        public Mapping getMapping() {
            return mapping;
        }
        public void setMapping(Mapping mapping) {
            this.mapping = mapping;
        }

        public void setColumns(List<Column> columns) {
            this.columns = columns;
            for (int i = 0; i < columns.size(); i++)
                this.columnsIdx.put(columns.get(i).column, i);
        }
        public void addColumn(String columnName, Column column) {
            if(columnsIdx.containsKey(columnName))
                columns.set(columnsIdx.get(columnName), column);
            else {
                columns.add(column);
                columnsIdx.put(column.column, columns.size()-1);
            }
        }
        public List<Column> getColumns() {
            return columns;
        }

        /** Returns null if column doesn't exist */
        public Column getColumn(String columnName) {
            try {
                return columns.get(columnsIdx.get(columnName));
            }catch (NullPointerException e){
                return null;
            }
        }

        public ArrayList<String> getFilenames() {
            return filenames;
        }
        public void setFilenames(ArrayList<String> filenames) {
            this.filenames = filenames;
        }
        public ArrayList<String> getDocIds() {
            return docIds;
        }
        public void setDocIds(ArrayList<String> docIds) {
            this.docIds = docIds;
        }


    }
    //================================================================================

    /**
     * Such as a column in a relational table or csv file
     * or a JSON attribute
     */
    public static class Column {
        private String column;
        private boolean isPii;
        private List<Mapping> mappings;

        public Column(String field) {
            this.column = field;
            this.mappings = new ArrayList<>();
        }

        public String getColumn() {
            return column;
        }
        public void setColumn(String column) {
            this.column = column;
        }

        /** is this column a pii attribute according to the preprocessing ? (isPii stores the output of the preprocessing) */
        public boolean isPii() {
            return isPii;
        }
        public void setPii(boolean pii) {
            isPii = pii;
        }

        public void addMapping(Mapping mapping) {
            this.mappings.add(mapping);
        }
        public void setMappings(List<Mapping> mappings) {
            this.mappings = mappings;
        }
        public List<Mapping> getMappings() {
            return mappings;
        }

        public Mapping getClassPropMapping() {
            return returnMappingPerType(0);
        }
        public Mapping getObjectPropMapping() {
            return returnMappingPerType(1);
        }
        public Mapping getDataPropMapping() {
            return returnMappingPerType(2);
        }
        private Mapping returnMappingPerType(int type) {
            try {
                return mappings.get(type);
            }catch (IndexOutOfBoundsException e) {
                return new Mapping(null,"", null, null);
            }
        }

        public void delClassPropMapping() {
            delMappingPerType(0);
        }
        public void delObjectPropMapping() {
            delMappingPerType(1);
        }
        public void delDataPropMapping() {
            delMappingPerType(2);
        }
        private void delMappingPerType(int type) {mappings.set(type, null);}
    }

    //================================================================================

    public static class Mapping {
        private String type;
        private URI ontoEl;
        private List<URI> match;
        private List<URI> path;
        // store the resources that this po el was initially mapped to (useful during pii identification)
        private List<URI> initialMatch;

        public Mapping(String type, String ontoEl, List<URI> match, List<URI> path) {
            this.type   = type;
            this.ontoEl = URI.create(ontoEl);
            if(match == null)
                this.match = new ArrayList<>();
            if(path != null)
                this.path = path;
            initialMatch = null;
        }

        // type ----------------------------------------------------------------
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean hasDataProperty() {
            return type != null;
        }

        // ontoEl ----------------------------------------------------------------
        public URI getOntoElURI() {
            return ontoEl;
        }

        public String getOntoElResource() {return Ontology.getLocalName(ontoEl);}

        public void setOntoEl(URI ontoEl) {
            this.ontoEl = ontoEl;
        }

        // match ----------------------------------------------------------------
        public List<URI> getMatchURI() {
            return match;
        }

        public void setMatch(List<URI> match) {
            this.match = (match != null) ? match : new ArrayList<>();
        }
        public boolean hasMatch() {
            return match.size() > 0;
        }

        public void setAsInitialMatch() {
            initialMatch = match;
            setMatch(null);
        }

        public List<URI> getInitialMatch() {
            return initialMatch;
        }
        public void setInitialMatch(List<URI> initialMatch) {
            this.initialMatch = initialMatch;
        }

        // path ----------------------------------------------------------------
        public List<URI> getPathURIs() {
            return path;
        }

        public List<String> getPathResources() {
            return path.stream()
                    .map(Ontology::getLocalName)
                    .collect(Collectors.toList());
        }
        public boolean hasPath() {
            return path != null;
        }
        public void setPath(List<URI> path) {
            this.path = path;
        }
        public URI getFirstNodeFromPath() {
            return path.get(0);
        }
        public URI getLastNodeFromPath() {
            return path.get(path.size() - 1);
        }

    }
    //================================================================================

}
