package org.example.ontology_extractor;

import java.util.*;

public class Properties{
    public static class DomRan {
        Set<String> domain;
        Set<String> range;
        Set<String> rule;

        public DomRan(String rule, String domain, String range) {
            this.rule = new HashSet<>(Collections.singleton(rule));
            this.domain = new HashSet<>(Collections.singleton(domain));
            this.range = new HashSet<>(Collections.singleton(range));
        }
        public void makeUnion(String rule, String domain, String range) {
            this.rule.add(rule);
            this.domain.add(domain);
            this.range.add(range);
        }
    }
    private HashMap<String, DomRan> properties;

    public Properties(){
        properties = new HashMap<>();
    }
    public void addProperty(String rule, String domain, String propertyName, String range) {
        if(propertyName == null)
            propertyName = pName(domain, range);

        if(properties.containsKey(propertyName))
            properties.get(propertyName).makeUnion(rule, domain, range);
        else
            properties.put(propertyName, new DomRan(rule, domain, range));
    }

    private String pName(String domain, String range) {
        return String.format("%s_%s", domain, range);
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


}
















