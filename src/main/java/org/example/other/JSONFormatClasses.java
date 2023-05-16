package org.example.other;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static org.example.other.Util.getURIResource;

public class JSONFormatClasses {

    private List<Table> tables;

    public List<Table> getTables() {
        return tables;
    }

    public void setTables(List<Table> tables) {
        this.tables = tables;
    }

    //================================================================================

    public static class Table {
        private String table;
        private Mapping mapping;
        private List<Column> columns;

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

        public void setColumns(List<Column> columns) {
            this.columns = columns;
        }
    }

    //================================================================================

    public static class Column {
        private String column;
        private List<Mapping> mappings;

        public String getColumn() {
            return column;
        }

        public void setColumn(String column) {
            this.column = column;
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
            return mappings.get(type);
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
    }
    //================================================================================

}

