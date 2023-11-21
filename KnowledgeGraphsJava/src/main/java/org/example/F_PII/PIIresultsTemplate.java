package org.example.F_PII;
import org.example.MappingsFiles.MappingsFileTemplate.Source;

import java.util.*;

import static org.example.A_Coordinator.Pipeline.config;

public class PIIresultsTemplate {
    String domain = config.In.UseCase;
    List<PIIattribute> PIIattributes = new ArrayList<>();

    public List<PIIattribute> getPIIattributes() {
        return PIIattributes;
    }

    public void setPIIattributes(List<PIIattribute> PIIattributes) {
        this.PIIattributes = PIIattributes;
    }

    public void addPIIattribute(PIIattribute piiAttribute){
        PIIattributes.add(piiAttribute);
    }

    public void sortPiiAttributesList() {
        PIIattributes.sort(Comparator.comparing(PIIattribute::getDatasetElement));
    }
}

class PIIattribute {
    private String datasetElement;
    private Set<Source> sources = new HashSet<>();
    //private ArrayList<String> knowledgeGraphURI;
    private boolean isPersonalData;
    private boolean isIdentifying;
    private boolean isSpecialCategoryPersonalData;
    private List<DpvMatch> dpvMatches = new ArrayList<>();

    public void addMatch(DpvMatch match) {
        dpvMatches.add(match);
    }

    public String getDatasetElement() {
        return datasetElement;
    }

    public void setDatasetElement(String datasetElement) {
        this.datasetElement = datasetElement;
    }

    public List<DpvMatch> getDpvMatches() {
        return dpvMatches;
    }

    public void setDpvMatches(List<DpvMatch> dpvMatches) {
        this.dpvMatches = dpvMatches;
    }

    /*public ArrayList<String> getKnowledgeGraphURI() {
        return knowledgeGraphURI;
    }

    public void setKnowledgeGraphURI(ArrayList<String> knowledgeGraphURI) {
        this.knowledgeGraphURI = knowledgeGraphURI;
    }*/

    public boolean isPersonalData() {
        return isPersonalData;
    }
    public void setPersonalData(boolean personalData) {
        isPersonalData = personalData;
    }

    public boolean isIdentifying() {
        return isIdentifying;
    }
    public void setIdentifying(boolean identifying) {
        isIdentifying = identifying;
    }

    public boolean isSpecialCategoryPersonalData() {
        return isSpecialCategoryPersonalData;
    }
    public void setSpecialCategoryPersonalData(boolean specialCategoryPersonalData) {
        isSpecialCategoryPersonalData = specialCategoryPersonalData;
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
    private List<IsSubclassOf> isSubclassOf = new ArrayList<>();
    private transient HashSet<String> superClasses = new HashSet<>();

    public void addSuperclass(IsSubclassOf superClass) {
        isSubclassOf.add(superClass);
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

    public List<IsSubclassOf> getIsSubclassOf() {
        return isSubclassOf;
    }

    public void setIsSubclassOf(List<IsSubclassOf> isSubclassOf) {
        this.isSubclassOf = isSubclassOf;
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

