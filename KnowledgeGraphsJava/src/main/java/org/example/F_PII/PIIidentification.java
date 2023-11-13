package org.example.F_PII;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.RDFNode;
import org.example.util.JsonUtil;
import org.example.MappingsFiles.MappingsFileTemplate.Column;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;
import org.example.util.Ontology;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.example.MappingsFiles.ManageMappingsFile.readMapJSON;

public class PIIidentification {

    /*String PO2DO = "src/main/resources/Use_Case/Fintech/EPIBANK/Other/PO2DO_Mappings.json";
    String POPath = "src/main/resources/Use_Case/Fintech/EPIBANK/KG_outputs/POntology.ttl";
    String DOPath = "src/main/resources/Use_Case/Fintech/DOntology/FIBOLt.owl";
    String dpvPath = "src/main/resources/PII/dpv-pii.ttl";
    String DO2dpvPath = "src/main/resources/PII/Fintech2DPV.json";
    String T41PIIsPath = "src/main/resources/Use_Case/Fintech/EPIBANK/T41PIIs.json";

    String personalDataURI = "https://w3id.org/dpv/dpv-owl#PersonalData";
    String specialPersonalDataURI = "https://w3id.org/dpv/dpv-owl#SpecialCategoryPersonalData";


    private Ontology POnto;
    private Ontology DOnto;
    private Ontology dpvOnto;
    private List<Table> tablesList;
    private HashMap<String, ArrayList<String>> do2dpv;
    private HashMap<String, Set<String>> PIIs;

    private PIIresultsTemplate PiiResults;
    private HashSet<String> KGPiis = new HashSet<>();

    public PIIidentification() {
        POnto = new Ontology(POPath);
        DOnto = new Ontology(DOPath);
        dpvOnto = new Ontology(dpvPath);
        PIIs = new HashMap<>();
        PiiResults = new PIIresultsTemplate();
        crossMapping();
        extractResults();
        appendT41piisList();
        JsonUtil.saveToJSONFile("fintech-piis.json", PiiResults);

        System.out.println(this);
    }

//=============================================================================================================
// Cross mapping
//=============================================================================================================
    private void loadDO2dpvMappings() {
        do2dpv = new HashMap<>();
        JsonObject maps = JsonUtil.readJSON(DO2dpvPath).getAsJsonObject();
        for(String dpvEl : maps.keySet()) {
            for(JsonElement doElJson : maps.get(dpvEl).getAsJsonArray()) {
                String doEl = doElJson.getAsString();
                if(do2dpv.containsKey(doEl))
                    do2dpv.get(doEl).add(dpvEl);
                else
                    do2dpv.put(doEl, new ArrayList<>(){{add(dpvEl);}});
            }
        }
    }

    private void crossMapping() {
        try {
            loadDO2dpvMappings();
        }catch (Exception e){ return; } //DO2DPV file not found -> don't add cross mappings
        tablesList = readMapJSON(PO2DO);
        for(Table table : tablesList) {
            crossMapping(table.getMapping());
            for (Column col : table.getColumns()) {
                crossMapping(col.getObjectPropMapping());
                crossMapping(col.getClassPropMapping());
                crossMapping(col.getDataPropMapping());
            }
        }
    }

    private void crossMapping(Mapping elMap) {
        String ontoEl = elMap.getOntoElURI().toString();
        if(elMap.hasMatch()) {
            String match = elMap.getMatchURI().toString();
            Set<String> matchAncestors = DOnto.getAncestors(match, true).keySet();
            matchAncestors.forEach(ancestor -> {
                if(do2dpv.containsKey(ancestor))
                    do2dpv.get(ancestor).forEach(dpvClass -> addDetectedPII(ontoEl, dpvClass));
            });
        }
    }
//=============================================================================================================
// Cross mapping
//=============================================================================================================
    private void addDetectedPII(String ontoEl, String dpvClass) {
        if(PIIs.containsKey(ontoEl))
            PIIs.get(ontoEl).add(dpvClass);
        else
            PIIs.put(ontoEl, new HashSet<>(){{add(dpvClass);}});
    }

//=============================================================================================================
// Extract results
//=============================================================================================================
    private void extractResults() {
        HashMap<String, ArrayList<String>> piiFields = new HashMap<>();
        for(String ontoElURI : PIIs.keySet()) {
            OntResource ontoEl = POnto.getOntResource(ontoElURI);
            String extractedFieldString = null;
            for (RDFNode comment : ontoEl.listComments(null).toList()) {
                Matcher matcher = Pattern.compile("\\[(.*?)\\]").matcher(comment.asLiteral().getString());
                if (matcher.find()) {
                    extractedFieldString = matcher.group(1);
                    break;
                }
            }
            assert extractedFieldString != null;
            for(String datasetEl : extractedFieldString.split(",")) {
                datasetEl = datasetEl.trim();
                KGPiis.add(datasetEl);
                if(piiFields.containsKey(datasetEl))
                    piiFields.get(datasetEl).add(ontoElURI);
                else
                    piiFields.put(datasetEl, new ArrayList<>(){{add(ontoElURI);}});
            }
        }
        // -----------------------------------------------------
        piiFields.forEach((datasetEl, ontoElements) -> {
            HashSet<String> detectedMatches = new HashSet<>();

            PIIattribute piiAttr = new PIIattribute();
            piiAttr.setDatasetElement(datasetEl);
            // piiAttr.setKnowledgeGraphURI(ontoElements);
            boolean isPersonalData = false, isSpecialPersonalData = false;

            for(String ontoElURI : ontoElements) {
                for(String dpvClass : PIIs.get(ontoElURI)) {

                    // -----------------------------------------------------
                    if(detectedMatches.contains(dpvClass))
                        continue;
                    detectedMatches.add(dpvClass);
                    boolean isNewMatch = true;
                    OntClass dpvOntClass = dpvOnto.getOntClass(dpvClass);
                    for(String detectedMatch : detectedMatches)
                        if(dpvOntClass.hasSubClass(dpvOnto.getOntClass(detectedMatch), false)) {
                            isNewMatch = false;
                            break;
                        }
                    if(!isNewMatch)
                        continue;
                    // -----------------------------------------------------

                    DpvMatch dpvMatch = new DpvMatch();
                    dpvMatch.setMatch(dpvClass);
                    dpvMatch.setLabel(getDPVAnnot(dpvClass, true));
                    dpvMatch.setDescription(getDPVAnnot(dpvClass, false));

                    for (Map.Entry<String, Integer> entry : getAncestors(dpvClass).entrySet()) {
                        String ancestor = entry.getKey();
                        Integer depth = entry.getValue();
                        IsSubclassOf ancestorInfo = new IsSubclassOf();
                        ancestorInfo.setUri(ancestor);
                        ancestorInfo.setLabel(getDPVAnnot(ancestor, true));
                        ancestorInfo.setDescription(getDPVAnnot(ancestor, false));
                        ancestorInfo.setDepth(depth);
                        dpvMatch.addSuperclass(ancestorInfo);

                        if(personalDataURI.equals(ancestor))
                            isPersonalData = true;
                        if(specialPersonalDataURI.equals(ancestor))
                            isSpecialPersonalData = true;
                    }
                    piiAttr.addMatch(dpvMatch);
                    piiAttr.setPersonalData(isPersonalData);
                    piiAttr.setSpecialCategoryPersonalData(isSpecialPersonalData);
                }
            }
            PiiResults.addPIIattribute(piiAttr);
        });
    }



    private Map<String, Integer> getAncestors(String dpvClass) {
        List<Map.Entry<String, Integer>> sortedAncestors = new ArrayList<>(dpvOnto.getAncestors(dpvClass, false).entrySet());
        Comparator<Map.Entry<String, Integer>> comparator = Map.Entry.comparingByValue();
        sortedAncestors.sort(comparator);
        Map<String, Integer> sortedAncestorsMap = sortedAncestors.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
        return sortedAncestorsMap;
    }

    private String getDPVAnnot(String resourceURI, boolean getLabels) {
        String annotProp = getLabels ? "rdfs:label" : "<http://purl.org/dc/terms/description>";
        String query = Ontology.swPrefixes() + "\n" +
                "select ?annots where {\n" +
                "   <" + resourceURI + "> " + annotProp + " ?annots . }";
        String annot= dpvOnto.runQuery(query, new String[]{"annots"}).stringColumn("annots").get(0);
        if(annot.contains("@"))
            annot = annot.substring(0, annot.lastIndexOf("@"));
        return annot;
    }

//=============================================================================================================
// Append T4.1 PIIs list
//=============================================================================================================
    private void appendT41piisList() {
        List<JsonElement> piisList = JsonUtil.readJSON(T41PIIsPath).getAsJsonObject().get("PIIs").getAsJsonArray().asList();
        for(JsonElement datasetElJson : piisList) {
            String datasetEl = datasetElJson.getAsString();
            boolean isIncluded = false;
            for(String kgPii : KGPiis) {
                if(kgPii.endsWith(datasetEl)) {
                    isIncluded = true;
                    break;
            }}
            if(!isIncluded) {
                PIIattribute piiAttr = new PIIattribute();
                piiAttr.setDatasetElement(datasetEl);
                piiAttr.setPersonalData(true);
                piiAttr.setSpecialCategoryPersonalData(false);
                PiiResults.addPIIattribute(piiAttr);
            }
        }
    }


//=============================================================================================================

    @Override
    public String toString() {
        StringBuilder bd = new StringBuilder();
        for(String ontoEl : PIIs.keySet()){
            bd.append("\n>> ").append(ontoEl);
            PIIs.get(ontoEl).forEach(dpvClass ->{
                bd.append("\n\t").append(dpvClass);
            });
        }
        return bd.toString();
    }

    public static void main(String[] args) {
        new PIIidentification();
    }*/

}













