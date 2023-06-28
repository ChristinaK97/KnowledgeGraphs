package org.example.mappingsFiles;

import org.example.other.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.example.other.Util.getURIResource;

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
     * Such as a relation table or a whole CSV file
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
                return new Mapping(null,"","", false, null);
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
        private URI match;
        private boolean isCons;
        private List<URI> path;

        public Mapping(String type, String ontoEl, String match, boolean isCons, List<URI> path) {
            this.type = type;
            this.ontoEl = URI.create(ontoEl);
            this.match = URI.create(match);
            if(path != null)
                this.path = path;
            this.isCons = isCons;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public URI getOntoElURI() {
            return ontoEl;
        }

        public String getOntoElResource() {return getURIResource(ontoEl);}

        public void setOntoEl(URI ontoEl) {
            this.ontoEl = ontoEl;
        }

        public URI getMatchURI() {
            return match;
        }
        public String getMatchResource() {
            return getURIResource(match);
        }

        public void setMatch(URI match) {
            this.match = match;
        }

        public boolean isCons() {
            return isCons;
        }

        public void setCons(boolean cons) {
            isCons = cons;
        }

        public List<URI> getPathURIs() {
            return path;
        }

        public List<String> getPathResources() {
            return path.stream()
                    .map(Util::getURIResource)
                    .collect(Collectors.toList());
        }

        public void setPath(List<URI> path) {
            this.path = path;
        }

        public boolean hasMatch() {
            return !"".equals(match.toString());
        }

        public boolean hasDataProperty() {
            return type != null;
        }
    }
    //================================================================================

}
