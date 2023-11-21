package org.example.F_PII;
import org.example.MappingsFiles.MappingsFileTemplate.Source;

import java.util.*;

public class PIIresultsTemplate {
    String domain;
    List<PIIattribute> pii_attributes = new ArrayList<>();

    public String getDomain() {
        return domain;
    }
    public void setDomain(String domain) {
        this.domain = domain;
    }

    public List<PIIattribute> getPii_attributes() {
        return pii_attributes;
    }

    public void setPii_attributes(List<PIIattribute> pii_attributes) {
        this.pii_attributes = pii_attributes;
    }

    public void addPII_attribute(PIIattribute pii_attribute){
        pii_attributes.add(pii_attribute);
    }

    public void sortPiiAttributesList() {
        pii_attributes.sort(Comparator.comparing(PIIattribute::getDataset_element));
    }
}

class PIIattribute {
    private String dataset_element;
    private Set<Source> sources = new HashSet<>();
    //private ArrayList<String> knowledgeGraphURI;
    private boolean is_personal_data;
    private boolean is_identifying;
    private boolean is_special_category_personal_data;
    private List<DpvMatch> dpv_matches = new ArrayList<>();

    public void addMatch(DpvMatch match) {
        dpv_matches.add(match);
    }

    public String getDataset_element() {
        return dataset_element;
    }

    public void setDataset_element(String dataset_element) {
        this.dataset_element = dataset_element;
    }

    public List<DpvMatch> getDpv_matches() {
        return dpv_matches;
    }

    public void setDpv_matches(List<DpvMatch> dpv_matches) {
        this.dpv_matches = dpv_matches;
    }

    /*public ArrayList<String> getKnowledgeGraphURI() {
        return knowledgeGraphURI;
    }

    public void setKnowledgeGraphURI(ArrayList<String> knowledgeGraphURI) {
        this.knowledgeGraphURI = knowledgeGraphURI;
    }*/

    public boolean isIs_personal_data() {
        return is_personal_data;
    }
    public void setIs_personal_data(boolean is_personal_data) {
        this.is_personal_data = is_personal_data;
    }

    public boolean isIs_identifying() {
        return is_identifying;
    }
    public void setIs_identifying(boolean is_identifying) {
        this.is_identifying = is_identifying;
    }

    public boolean isIs_special_category_personal_data() {
        return is_special_category_personal_data;
    }
    public void setIs_special_category_personal_data(boolean is_special_category_personal_data) {
        this.is_special_category_personal_data = is_special_category_personal_data;
    }

    public Set<Source> getSources() {
        return sources;
    }
    public void addSource(Source source) {
        sources.add(source);
    }
    public void addSources(Set<Source> sources) {
        this.sources.addAll(sources);
    }
}

class DpvMatch {
    private String match;
    private String label;
    private String description;
    private List<IsSubclassOf> is_subclass_of = new ArrayList<>();
    private transient HashSet<String> superClasses = new HashSet<>();

    public void addSuperclass(IsSubclassOf superClass) {
        is_subclass_of.add(superClass);
        superClasses.add(superClass.getUri());
    }
    public boolean hasSuperClass(String dpvClass) {
        return superClasses.contains(dpvClass);
    }

    public String getMatch() {
        return match;
    }

    public void setMatch(String match) {
        this.match = match;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<IsSubclassOf> getIs_subclass_of() {
        return is_subclass_of;
    }

    public void setIs_subclass_of(List<IsSubclassOf> is_subclass_of) {
        this.is_subclass_of = is_subclass_of;
    }
}

class IsSubclassOf {
    private String uri;
    private String label;
    private String description;
    private int depth;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}

