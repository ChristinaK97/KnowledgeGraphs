package org.example.other;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Util {

    public static final String resourcePath = "src/main/resources/";

    public static final String SQL_DDL = resourcePath + "EFS_SQL_DDL_MySQL.sql";
    public static final String simulatedDataFull = resourcePath + "simulated_data_v2/";
    public static final String simulatedDataSample = resourcePath + "simulated_data_v2 - sample/";

    public static final String DOontology = resourcePath + "FIBOFull.ttl";
    public static final String POontology = resourcePath + "test_efs.ttl";

    public static final String TableWithMappings = resourcePath + "TableWithMappings.csv";
    public static final String EFS_mappings = resourcePath + "EFS_mappings.json";
    public static final String EFS_mappings_ObjProp = resourcePath + "EFS_mappings_ObjProp.json";


    public static final String outputOntology = resourcePath + "outputOntology.ttl";
    public static final String mergedOutputOntology = resourcePath + "mergedOutputOntology.ttl";

    public static final String individualsTTL = resourcePath + "individuals.ttl";
    public static final String pathsTXT = resourcePath + "paths.txt";
    public static final String fullGraph = resourcePath + "fullGraph.ttl";
    public static final String sampleGraph = resourcePath + "sampleGraph.ttl";


    public static final String TABLE_CLASS = "TableClass";

    public static URI get_TABLE_CLASS_URI(String ontologyName) {
        return URI.create("http://www.example.net/ontologies/" + ontologyName + ".owl/TableClass");
    }
    public static URI get_ATTRIBUTE_PROPERTY_URI(String ontologyName) {
        return URI.create("http://www.example.net/ontologies/" + ontologyName + ".owl/AttributeProperty");
    }
    public static URI get_FK_PROPERTY_URI(String ontologyName) {
        return URI.create("http://www.example.net/ontologies/" + ontologyName + ".owl/FKProperty");
    }


    public static String normalise(String s) {
        return normalise(new HashSet<>(Collections.singleton(s)));
    }

    public static String normalise(Set<String> s){
        return s.toString()
                           .replaceAll("[\\[\\],]","")
                           .replaceAll("_", " ")
                           .replace("p ", "")
                           .replace(" VALUE", "")
                           .replace(" ATTR", "");
    }

    public static String getURIResource(URI uri) {
        return getURIResource(uri.getPath());

    }
    public static String getURIResource(String uri) {
        return uri.substring(uri.lastIndexOf('/') + 1);
    }
}
