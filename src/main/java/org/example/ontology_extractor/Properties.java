package org.example.ontology_extractor;

import org.example.other.Util;

import java.util.*;

public class Properties{
    public static class DomRan {
        Set<String> domain;
        Set<String> range;
        Set<String> rule;
        Set<String> extractedField;

        public DomRan(String rule, String domain, String range, String extractedField) {
            this.rule = new HashSet<>(Collections.singleton(rule));
            this.domain = new HashSet<>(Collections.singleton(domain));
            this.range = new HashSet<>(Collections.singleton(range));
            this.extractedField = new HashSet<>(Collections.singleton(extractedField));
        }
        public void makeUnion(String rule, String domain, String range, String extractedField) {
            this.rule.add(rule);
            this.domain.add(domain);
            this.range.add(range);
            this.extractedField.add(extractedField);
        }
        public String getObjectPropertyLabel() {return "has_" + Util.normalise(range);}
        public String getInverse() {return String.format("p_%s_%s", Util.normalise(range), Util.normalise(domain));}

        public Set<String> getExtractedField() {
            return extractedField;
        }
    }

    // ==============================================================================================================
    private HashMap<String, DomRan> properties;

    public Properties(){
        properties = new HashMap<>();
    }

    public void addProperty(String rule, String domain, String propertyName, String range, String extractedField) {
        if(propertyName == null)
            propertyName = pName(domain, range);

        if(properties.containsKey(propertyName))
            properties.get(propertyName).makeUnion(rule, domain, range, extractedField);
        else
            properties.put(propertyName, new DomRan(rule, domain, range, extractedField));
    }

    private String pName(String domain, String range) {
        return String.format("p_%s_%s", domain, range);
    }

    public HashMap<String, DomRan> getProperties() {
        return properties;
    }


    @Override
    public String toString() {
        StringBuilder bd = new StringBuilder();
        properties.forEach((propName, domRan) -> {
            bd.append(String.format("(%s, %s, %s) %s\n", domRan.domain,
                    propName, domRan.range, domRan.rule));
        });
        return bd.toString();
    }

    public boolean contains(String propertyName) {
        return properties.containsKey(propertyName);
    }

}
















