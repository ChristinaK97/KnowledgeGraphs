package org.example.util;

import org.semanticweb.owlapi.model.IRI;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Util {

    public static final String resourcePath = "src/main/resources/";

    // do and po ontologies
    public static final String DOontology = resourcePath + "dicom.owl";
    public static final String POontology = resourcePath + "POntology.ttl";

    // po to do mappings
    public static final String TableWithMappings = resourcePath + "TableWithMappings.csv";
    public static final String PO2DO_Mappings = resourcePath + "PO2DO_Mappings.json";
    public static final String PO2DO_Mappings_ObjProp = resourcePath + "PO2DO_Mappings_ObjProp.json";


    // output ontology
    public static final String outputOntology = resourcePath + "outputOntology.ttl";
    public static final String mergedOutputOntology = resourcePath + "mergedOutputOntology.ttl";

    // final knowledge graph
    public static final String individualsTTL = resourcePath + "individuals.ttl";
    public static final String pathsTXT = resourcePath + "paths.txt";
    public static final String fullGraph = resourcePath + "fullGraph.ttl";
    public static final String sampleGraph = resourcePath + "sampleGraph.ttl";


    // extra po elements
    public static final IRI skosIRI = IRI.create("http://www.w3.org/2004/02/skos/core#");

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


    // acquire resource label
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

    // extract the local name of a uri
    public static String getLocalName(URI uri) {
        return getLocalName(uri.getPath());

    }
    public static String getLocalName(String uri) {
        return uri.substring(uri.lastIndexOf('/') + 1);
    }
}
