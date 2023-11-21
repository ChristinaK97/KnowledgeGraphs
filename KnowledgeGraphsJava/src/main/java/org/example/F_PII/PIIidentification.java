package org.example.F_PII;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.MappingsFiles.MappingsFileTemplate;
import org.example.util.JsonUtil;
import org.example.MappingsFiles.MappingsFileTemplate.Column;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;
import org.example.MappingsFiles.MappingsFileTemplate.Source;
import org.example.util.Ontology;
import org.example.util.Pair;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.example.A_Coordinator.Pipeline.config;
import static org.example.B_InputDatasetProcessing.DICOM.DICOMUtil.getNameFromCode;
import static org.example.MappingsFiles.ManageMappingsFile.readMapJSONasTemplate;

public class PIIidentification {

    static class OntoElInfo {
        // pairs of <tableName,columnName> that were transformed to this ontoEl
        HashSet<Pair<String, String>> datasetEls = new HashSet<>();
        // the dpv classes that this ontoEl was associated with
        HashSet<String> dpvMatches = new HashSet<>();

        public OntoElInfo(String tableName, String colName, String dpvClass) {
            addDatasetEl(tableName, colName);
            addDpvMatch(dpvClass);
        }

        public void addDatasetEl(String tableName, String colName) {
            datasetEls.add(new Pair<>(tableName, colName));
        }
        public void addDpvMatch(String dpvClass) {
            dpvMatches.add(dpvClass);
        }
    }
    //------------------------------------------------------------------------------------------------------------------

    String personalDataURI = "https://w3id.org/dpv/dpv-owl#PersonalData";
    String specialPersonalDataURI = "https://w3id.org/dpv/dpv-owl#SpecialCategoryPersonalData";
    String identifyingURI = "https://w3id.org/dpv/dpv-owl/dpv-pd#Identifying";


    private Ontology DOnto;
    private Ontology dpvOnto;
    private MappingsFileTemplate tablesList;

    private HashMap<String, ArrayList<String>> do2dpv;
    private boolean doCrossMapping;

    private HashMap<String, OntoElInfo> PIIs;

    private PIIresultsTemplate PiiResults;
    private HashSet<Pair<String,String>> KGPiis = new HashSet<>();


    public PIIidentification() {
        doCrossMapping = config.PiiMap.UseCase2DPV_file_path != null;
        DOnto   = doCrossMapping ? new Ontology(config.DOMap.TgtOntology) : null;
        dpvOnto = new Ontology(config.PiiMap.TgtOntology);

        tablesList = readMapJSONasTemplate();

        PIIs = new HashMap<>();
        PiiResults = new PIIresultsTemplate();
        PiiResults.setDomain(config.In.UseCase);

        doCrossMapping = config.PiiMap.UseCase2DPV_file_path != null;
        loadDO2dpvMappings();

        findPiis();

        extractResults();
        appendT41piisList();
        parseJsonColumnNames(); // only applicable for json and dicom input
        groupByDatasetElement();
        cleanupHierarchies();
        PiiResults.sortPiiAttributesList();
        JsonUtil.saveToJSONFile(config.PiiMap.PiisResultsJsonPath, PiiResults);

        System.out.println(this);
    }

    public PIIresultsTemplate getPiiResults() {
        return PiiResults;
    }

    //=============================================================================================================
// Find piis mapping
//=============================================================================================================

    // the table and column that are examined at the moment
    private String tableName;
    private String colName;

    private void findPiis() {
        for(Table table : tablesList.getTables()) {
            this.tableName = table.getTable();
            this.colName = this.tableName; // the table itself might store piis->it's match with dpv class

            // table pii
            String tableClass = table.getMapping().getOntoElURI().toString();
            addBertMapDPVmappings(tableClass, table.getDpvMappings());
            if(doCrossMapping)
                crossMapping(table.getMapping());

            for (Column col : table.getColumns()) {
                this.colName = col.getColumn();

                // attribute/column class pii
                String attrClass = col.getClassPropMapping().getOntoElResource();
                addBertMapDPVmappings(attrClass, col.getDpvMappings());

                if(doCrossMapping) {
                    crossMapping(col.getObjectPropMapping());
                    crossMapping(col.getClassPropMapping());
                    crossMapping(col.getDataPropMapping());
                }
            }//end_column
        }//end_table
    }

    private void addDetectedPII(String ontoEl, String dpvClass) {
        if(PIIs.containsKey(ontoEl)) {
            PIIs.get(ontoEl).addDatasetEl(this.tableName, this.colName);
            PIIs.get(ontoEl).addDpvMatch(dpvClass);
        }else {
            PIIs.put(ontoEl, new OntoElInfo(this.tableName, this.colName, dpvClass));
        }
    }

//=============================================================================================================
// BertMap DPV mappings
//=============================================================================================================

    private void addBertMapDPVmappings(String ontoEl, Set<URI> dpvMappings) {
        for(URI dpvClass: dpvMappings) {
            addDetectedPII(ontoEl, dpvClass.toString());
        }
    }

//=============================================================================================================
// Cross mapping
//=============================================================================================================

    private void loadDO2dpvMappings() {
        do2dpv = new HashMap<>();
        if(!doCrossMapping) // no cross mappings file was found
            return;

        JsonObject maps = JsonUtil.readJSON(config.PiiMap.UseCase2DPV_file_path).getAsJsonObject();
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

    private void crossMapping(Mapping elMap) {
        String ontoEl = elMap.getOntoElURI().toString();
        if(elMap.hasMatch())
            for(URI match : elMap.getMatchURI())
                crossMapElementMatch(ontoEl, match.toString());
        if(elMap.hasInitialMatch())
            for(URI initMatch : elMap.getInitialMatch())
                crossMapElementMatch(ontoEl, initMatch.toString());
    }

    private void crossMapElementMatch(String ontoEl, String match) {
        Set<String> matchAncestors = DOnto.getAncestors(match, true).keySet();
        matchAncestors.forEach(ancestor -> {
            if(do2dpv.containsKey(ancestor))
                do2dpv.get(ancestor).forEach(dpvClass -> addDetectedPII(ontoEl, dpvClass));
        });
    }


//=============================================================================================================
// Extract results
//=============================================================================================================

    private void extractResults() {
        // group PO ontoEl by tableName and columnName
        HashMap<Pair<String,String>, ArrayList<String>> piiFields = new HashMap<>();

        PIIs.forEach((ontoEl, info) -> {
            info.datasetEls.forEach(datasetEl -> {
                KGPiis.add(datasetEl);
                if(piiFields.containsKey(datasetEl))
                    piiFields.get(datasetEl).add(ontoEl);
                else
                    piiFields.put(datasetEl, new ArrayList<>(){{add(ontoEl);}});
        });});

        // -----------------------------------------------------
        piiFields.forEach((datasetEl, ontoElements) -> {

            PIIattribute piiAttr = new PIIattribute();

            // column name and identifiers
            piiAttr.setDataset_element(datasetEl.colName());
            piiAttr.addSources(getSources(datasetEl.tableName()));

            // piiAttr.setKnowledgeGraphURI(ontoElements);
            boolean isPersonalData = false, isIdentifying = false, isSpecialPersonalData = false ;

            for(String ontoEl : ontoElements) {
                for(String dpvClass : PIIs.get(ontoEl).dpvMatches) {

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
                        if(identifyingURI.equals(ancestor))
                            isIdentifying = true;
                        if(specialPersonalDataURI.equals(ancestor))
                            isSpecialPersonalData = true;
                    }
                    piiAttr.addMatch(dpvMatch);
                    piiAttr.setIs_personal_data(isPersonalData);
                    piiAttr.setIs_identifying(isIdentifying);
                    piiAttr.setIs_special_category_personal_data(isSpecialPersonalData);
                }
            }
            PiiResults.addPII_attribute(piiAttr);
        });
    }

    private Set<Source> getSources(String tableName) {
        return tablesList.getTable(tableName).getSources();
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

        for(Table table : tablesList.getTables()) {
            for(Column column : table.getColumns()) {

                if(  column.isPii() &&                                                  //was detected by T41 as pii and
                   ! KGPiis.contains(new Pair<>(table.getTable(), column.getColumn())) //was not already found by KGs
                ){
                    PIIattribute piiAttr = new PIIattribute();
                    piiAttr.setDataset_element(column.getColumn());
                    piiAttr.addSources(table.getSources());
                    piiAttr.setIs_personal_data(true);
                    piiAttr.setIs_identifying(false);
                    piiAttr.setIs_special_category_personal_data(false);
                    PiiResults.addPII_attribute(piiAttr);
                }
            }
        }

    }

//=============================================================================================================
// Group by column/datasetEl and clean up duplicates and hierarchies
//=============================================================================================================

    private void parseJsonColumnNames() {
        if(config.In.isJSON() || config.In.isDSON()) {
            PiiResults.getPii_attributes().forEach(piiAttr -> {
                String datasetEl = piiAttr.getDataset_element();
                datasetEl = datasetEl.substring(
                        datasetEl.lastIndexOf("/") + 1);
                if(config.In.isDSON())
                    datasetEl = String.format("%s|%s", datasetEl, getNameFromCode(datasetEl)); // datasetEl was initially (GGGG,EEEE) so turn to code|tag name
                piiAttr.setDataset_element(datasetEl);
            });
        }
    }

    private void groupByDatasetElement() {
        HashMap<String, PIIattribute> grouped = new HashMap<>();

        PiiResults.getPii_attributes().forEach(piiAttr -> {
            String datasetEl = piiAttr.getDataset_element();
            if(grouped.containsKey(datasetEl)) {
                grouped.get(datasetEl).addSources(piiAttr.getSources());
            }else {
                grouped.put(datasetEl, piiAttr);
            }
        });
        PiiResults.setPii_attributes(new ArrayList<>(grouped.values()));
    }


    private void cleanupHierarchies() {
        for(PIIattribute piiAttr : PiiResults.getPii_attributes()) {
            HashSet<Integer> toRmv = new HashSet<>();
            List<DpvMatch> dpvMatches = piiAttr.getDpv_matches();

            for (int i = 0; i < dpvMatches.size(); i++) {
                String match = dpvMatches.get(i).getMatch();
                for (int j = i+1; j < dpvMatches.size(); j++) {
                    // duplicate match or the current match i is a superclass of some other match j
                    if(match.equals(dpvMatches.get(j).getMatch()) || dpvMatches.get(j).hasSuperClass(match)) {
                        toRmv.add(i);
                    }}}
            List<DpvMatch> cleanedUpMatches = new ArrayList<>();
            for (int i = 0; i < dpvMatches.size(); i++) {
                if(!toRmv.contains(i)) {
                    cleanedUpMatches.add(dpvMatches.get(i));
                }}
            piiAttr.setDpv_matches(cleanedUpMatches);
        }
    }

//=============================================================================================================

    /*@Override
    public String toString() {
        StringBuilder bd = new StringBuilder();
        for(String ontoEl : PIIs.keySet()){
            bd.append("\n>> ").append(ontoEl);
            PIIs.get(ontoEl).forEach(dpvClass ->{
                bd.append("\n\t").append(dpvClass);
            });
        }
        return bd.toString();
    }*/


}













