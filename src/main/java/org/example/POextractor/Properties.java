package org.example.POextractor;

import org.example.util.Annotations;

import java.util.*;

import static org.example.util.Annotations.*;

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
        public String getObjectPropertyRawLabel() {return Annotations.getObjectPropertyRawLabel(range);}

        public String getInverse() {
            return inverseName(range, domain);
        }

        public static String getInverse(String propName) {
            return getInverseName(propName);
        }

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
            propertyName = pureObjPropName(domain, range);

        if(properties.containsKey(propertyName))
            properties.get(propertyName).makeUnion(rule, domain, range, extractedField);
        else
            properties.put(propertyName, new DomRan(rule, domain, range, extractedField));
    }


    public HashMap<String, DomRan> getProperties() {
        return properties;
    }

    public DomRan getPropertyDomRan(String propertyName) {
        return properties.get(propertyName);
    }

    public boolean contains(String propertyName) {
        return properties.containsKey(propertyName);
    }


    @Override
    public String toString() {
        StringBuilder bd = new StringBuilder();
        properties.forEach((propName, domRan) -> {
            bd.append(String.format("(%s, %s, %s) %s\t%s\n", domRan.domain,
                    propName, domRan.range, domRan.rule, domRan.extractedField));
        });
        return bd.toString();
    }

}
















