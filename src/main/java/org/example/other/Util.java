package org.example.other;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Util {

    public static final String TABLE_CLASS = "TableClass";
    public static final URI ATTRIBUTE_PROPERTY_URI = URI.create("http://www.example.net/ontologies/test_efs.owl/AttributeProperty");
    public static final URI TABLE_CLASS_URI = URI.create("http://www.example.net/ontologies/test_efs.owl/TableClass");;

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
