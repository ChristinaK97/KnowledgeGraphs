package org.example.MappingsFiles;

import org.example.util.Ontology;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class MappingsFileTemplate {

    public MappingsFileTemplate() {
        this.tables = new ArrayList<>();
    }

    private List<Table> tables;

    public List<Table> getTables() {
        return tables;
    }

    public void setTables(List<Table> tables) {
        this.tables = tables;
    }
    public void addTable(Table table) {
        tables.add(table);
    }

    //================================================================================

    /**
     * Such as a relation table or a CSV file
     */
    public static class Table {
        private String table;
        private Mapping mapping;
        private List<Column> columns;

        public Table(String element) {
            this.table = element;
            this.columns = new ArrayList<>();
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

        public List<Column> getColumns() {
            return columns;
        }

        public void addColumn(Column column) {
            columns.add(column);
        }

        public void setColumns(List<Column> columns) {
            this.columns = columns;
        }
    }
    //================================================================================

    /**
     * Such as a column in a relational table or csv file
     * or a JSON attribute
     */
    public static class Column {
        private String column;
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
            this.match = match != null ? match : new ArrayList<>();
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
