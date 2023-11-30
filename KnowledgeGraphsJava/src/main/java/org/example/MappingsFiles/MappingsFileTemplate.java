package org.example.MappingsFiles;

import org.example.util.Ontology;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;


public class MappingsFileTemplate {

    // TODO: metadata_id
    private String prev_metadata_id = null;
    private String current_metadata_id;

    private List<Table> tables;
    private transient HashMap<String, Integer> tablesIdx;

    public MappingsFileTemplate() {
        this.tables = new ArrayList<>();
        this.tablesIdx = new HashMap<>();
    }

    public void postDeserialization() {
        tablesIdx = new HashMap<>();
        for (int i = 0; i < tables.size(); i++) {
            this.tablesIdx.put(tables.get(i).table, i);
            tables.get(i).postDeserialization();
        }
    }

    public void setTables(List<Table> tables) {
        this.tables = tables;
        postDeserialization();
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


    public String getPrev_metadata_id() {return prev_metadata_id;}
    public void setPrev_metadata_id(String prev_metadata_id) {this.prev_metadata_id = prev_metadata_id;}
    public String getCurrent_metadata_id() {return current_metadata_id;}
    public void setCurrent_metadata_id(String current_metadata_id) {this.current_metadata_id = current_metadata_id;}

    //================================================================================

    /**
     * Such as a relation table or a CSV file
     */
    public static class Table {
        private String table;
        private Set<Source> sources;
        private Mapping mapping;
        private Set<URI> dpvMappings;

        private List<Column> columns;
        private transient HashMap<String, Integer> columnsIdx;

        public Table(String tableName) {
            this.table = tableName;
            this.columns = new ArrayList<>();
            this.columnsIdx = new HashMap<>();
            this.sources = new HashSet<>();
            this.dpvMappings = new HashSet<>();
        }
        public void copyTableSource(Table storedTable){
            this.sources = storedTable.sources;
        }

        // TODO: metadata_id
        public void addTableSource(String filename, String metadataId) {
            this.sources.add(new Source(
               filename, metadataId
            ));
        }

        public void postDeserialization() {
            columnsIdx = new HashMap<>();
            for (int i = 0; i < columns.size(); i++)
                columnsIdx.put(columns.get(i).column, i);
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
            postDeserialization();
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

        public Set<Source> getSources() {
            return sources;
        }
        public void setSources(Set<Source> sources) {
            this.sources = sources;
        }

        public Set<URI> getDpvMappings() {
            return dpvMappings;
        }
        public void setDpvMappings(Set<URI> dpvMappings) {
            this.dpvMappings = dpvMappings;
        }
        public void appendDPVMappings(List<URI> dpvMappings) {
            this.dpvMappings.addAll(dpvMappings);
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

        private Set<URI> dpvMappings;

        public Column(String field) {
            this.column = field;
            this.mappings = new ArrayList<>();
            this.dpvMappings = new HashSet<>();
        }

        public String getColumn() {
            return column;
        }
        public void setColumn(String column) {
            this.column = column;
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


        /** is this column a pii attribute according to the preprocessing ? (isPii stores the output of the preprocessing) */
        public boolean isPii() {
            return isPii;
        }
        public void setPii(boolean pii) {
            isPii = pii;
        }

        public Set<URI> getDpvMappings() {
            return dpvMappings;
        }
        public void setDpvMappings(Set<URI> dpvMappings) {
            this.dpvMappings = dpvMappings;
        }
        public void appendDPVMappings(List<URI> dpvMappings) {
            this.dpvMappings.addAll(dpvMappings);
        }
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
        public boolean hasInitialMatch() {
            return initialMatch != null && initialMatch.size()>0;
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

    // TODO: metadata_id
    public static class Source {
        private String filename;
        private String metadata_id;

        public Source(String filename, String metadata_id) {
            this.filename = filename;
            this.metadata_id = metadata_id;
        }

        public String getFilename() {
            return filename;
        }
        public void setFilename(String filename) {
            this.filename = filename;
        }
        public String getMetadata_id() {
            return metadata_id;
        }
        public void setMetadata_id(String metadata_id) {
            this.metadata_id = metadata_id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Source source = (Source) o;
            return
                filename.equals(source.filename) &&
                metadata_id.equals(source.metadata_id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filename, metadata_id);
        }
    }

}
