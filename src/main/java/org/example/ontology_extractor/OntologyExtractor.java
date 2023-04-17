package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;

public class OntologyExtractor {

    public static class Property{
        public String propertyName;
        public String domain;
        public String range;

        public Property(String propertyName, String domain, String range) {
            this.propertyName = propertyName;
            this.domain = domain;
            this.range = range;
        }
        public String toString() {
            return String.format("(%s, %s, %s)", domain, propertyName, range);
        }
    }

    private DBSchema db;
    private ClassExtractor classExtractor;
    private ObjectPropExtractor objPropExtractor;

    public OntologyExtractor(DBSchema db) {
        this.db = db;
        classExtractor = new ClassExtractor(db);
        objPropExtractor = new ObjectPropExtractor(db);
    }


    public static void main(String[] args) {
        new OntologyExtractor(new DBSchema());
    }

}
