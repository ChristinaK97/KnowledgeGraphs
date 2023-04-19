package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class OntologyExtractor {


    private DBSchema db;

    public OntologyExtractor(DBSchema db) {
        this.db = db;
        HashMap<String, String> convertedIntoClass = new ClassExtractor(db).getConvertedIntoClass();
        ArrayList<Property> objProperties = new ObjectPropExtractor(db, convertedIntoClass).getObjProperties();

        System.out.println(objProperties);
        DataPropExtractor dpExtr = new DataPropExtractor(db,true, convertedIntoClass);
        ArrayList<Property> dataProperties = dpExtr.getDataProperties();
        ArrayList<Property> newObjProp = dpExtr.getNewObjProp();
        objProperties.addAll(newObjProp);
        ArrayList<String> newClasses = dpExtr.getNewClasses();

    }


    public static void main(String[] args) {
        new OntologyExtractor(new DBSchema());
    }

}
