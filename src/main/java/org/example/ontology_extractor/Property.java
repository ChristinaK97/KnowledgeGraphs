package org.example.ontology_extractor;

import java.util.ArrayList;
import java.util.Collections;

public class Property {
    public String propertyName;
    private ArrayList<String> domain;
    private ArrayList<String> range;

    public Property(String propertyName, String domain, String range) {
        this.propertyName = propertyName;
        setDomain(domain);
        setRange(range);
    }
    public void setDomain(String domain) {
        this.domain = new ArrayList<>(Collections.singleton(domain));
    }
    public void setRange(String range) {
        this.range = new ArrayList<>(Collections.singleton(range));
    }
    public Object getDomain() {
        return domain.size()==1 ? domain.get(0) : domain;
    }
    public Object getRange() {
        return range.size()==1 ? range.get(0) : range;
    }
    public String toString() {
        return String.format("(%s, %s, %s)", getDomain(), propertyName, getRange());
    }
}
