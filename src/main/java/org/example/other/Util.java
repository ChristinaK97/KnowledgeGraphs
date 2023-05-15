package org.example.other;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Util {
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
        return uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
    }
}
