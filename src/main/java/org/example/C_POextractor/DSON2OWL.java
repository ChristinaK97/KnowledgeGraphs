package org.example.C_POextractor;

import static org.example.A_Coordinator.Runner.config;
import com.google.gson.JsonPrimitive;
import org.apache.jena.ontology.OntClass;
import org.example.B_InputDatasetProcessing.DICOM.TagDictionary;
import org.example.B_InputDatasetProcessing.JsonUtil;
import org.example.util.Ontology;
import tech.tablesaw.api.Table;

import java.util.Arrays;
import java.util.HashMap;

import static org.example.util.Annotations.*;
import static org.example.util.Ontology.getLocalName;

public class DSON2OWL extends JSON2OWL {

    private Ontology sedi;
    private TagDictionary tagDictionary;
    private HashMap<String, String> key_isSubclassOf_value = new HashMap<>();

    public DSON2OWL(TagDictionary tagDictionary) {
        super();
        this.tagDictionary = tagDictionary;
        sedi = new Ontology(config.Out.DOntology);
    }

    @Override
    protected void addObjectProperty(String domain, String range, String extractedField, String rule) {
        if(JsonUtil.isInvalidProperty(domain, range, extractedField))
            return;

        System.out.println(domain  + " " + range);
        String[] triple = getBroaderResources(domain, range);

        pureObjProperties.addProperty(
                rule,
                triple[0],
                triple[1],
                triple[2],
                extractedField
        );
                                                                                                                        if(print) System.out.println("\tADD OP: " + Arrays.toString(triple) + "\t\t" + rule);
    }

    private String[] getBroaderResources(String domainSubclass, String rangeSubclass) {

        String domain = domainSubclass;
        String range = rangeSubclass;
        String objProp = null;
        OntClass domainSuperclass = sedi.getTopSuperclass(domainSubclass);
        OntClass rangeSuperclass  = sedi.getTopSuperclass(rangeSubclass);

        System.out.println("TOP SUPER = " + domainSuperclass + " " + rangeSuperclass);

        if(domainSuperclass != null && rangeSuperclass != null) {
            String query = Ontology.swPrefixes() + "\n" +
                    "select ?objProp where { \n" +
                    "?objProp rdfs:domain <" + domainSuperclass + "> .\n" +
                    "?objProp rdfs:range <" + rangeSuperclass + "> .\n}";
            Table results = sedi.runQuery(query, new String[]{"objProp"});
            if (results.rowCount() > 0) {
                domain  = getLocalName(domainSuperclass);
                range   = getLocalName(rangeSuperclass);
                objProp = getLocalName(results.stringColumn("objProp").get(0));
                key_isSubclassOf_value.put(domainSubclass, domain);
                key_isSubclassOf_value.put(rangeSubclass, range);
            }
        }
        if(objProp == null) {
            if (domainSuperclass != null){
                domain = getLocalName(domainSuperclass);
                range  = rangeSubclass;
                key_isSubclassOf_value.put(domainSubclass, domain);
            }else if(rangeSuperclass != null){
                domain = domainSubclass;
                range  = getLocalName(rangeSuperclass);
                key_isSubclassOf_value.put(rangeSubclass, range);
            }
            objProp = pureObjPropName(domain, range);
        }
        /*for(String topClass : new String[]{domain, range})
            if(!tableClasses.containsKey(topClass))
                tableClasses.put(topClass, topClass);*/

        System.out.printf("NEW TRIPLE : < %s , %s , %s >\n", domain, objProp, range);
        return new String[]{domain, objProp, range};
    }



    @Override
    protected String getDataPropertyRange(JsonPrimitive value, String range) {
        return tagDictionary.getXsd_datatype(range);
    }


    public HashMap<String, String> getKey_isSubclassOf_value() {
        return key_isSubclassOf_value;
    }
}
