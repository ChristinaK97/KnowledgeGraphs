package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;

import java.util.ArrayList;
import java.util.HashMap;

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

    public OntologyExtractor(DBSchema db) {
        this.db = db;
        HashMap<String, String> convertedIntoClass = new ClassExtractor(db).getConvertedIntoClass();
        ArrayList<Property> objProperties = new ObjectPropExtractor(db, convertedIntoClass).getObjProperties();
    }


    public static void main(String[] args) {
        new OntologyExtractor(new DBSchema());
    }

}
