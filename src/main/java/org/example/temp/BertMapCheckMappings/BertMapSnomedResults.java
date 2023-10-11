package org.example.temp.BertMapCheckMappings;

import org.example.A_InputPoint.JsonUtil;
import org.example.util.Ontology;

import java.util.ArrayList;
import java.util.HashMap;

public class BertMapSnomedResults {

    public static void main(String[] args) {
        Ontology snomed = new Ontology("src/main/resources/medical_data/SNOMED_rdfxml.rdf");
        snomed.findAnnotationProperties(null, false);

        BertMapMappingsWrite.bertmap_results_json =
                "src/main/resources/Bertmap results/Bertmap results medical/bertmap results with abbrev expan 5 epochs.json";

        HashMap<String, ArrayList<String>> bertmapResults =
                new BertMapMappingsWrite(true).readBertmapMappings();
        HashMap<String, HashMap<String, ArrayList<String>>> results = new HashMap<>();

        bertmapResults.forEach((ontoEl, matches) -> {
            HashMap<String, ArrayList<String>> ontoElParsedMatches = new HashMap<>();
            int topPlace = 0;
            for(int i=0; i<matches.size() - 2 ; i+=3) {
                String snomedMatchURI = matches.get(i);
                String score = matches.get(i+1);
                String rank = matches.get(i+2);

                ArrayList<String> parsedMatch = new ArrayList<>(snomed.getResourceAnnotations(snomedMatchURI));
                parsedMatch.add(score);
                parsedMatch.add(rank);
                parsedMatch.add("# " + (topPlace++));

                ontoElParsedMatches.put(snomedMatchURI, parsedMatch);
            }
            results.put(ontoEl, ontoElParsedMatches);
        });
        //System.out.println(results);
        JsonUtil.saveToJSONFile(
                "src/main/resources/Bertmap results/Bertmap results medical/bertmap results with abbrev expan 5 epochs parsed.json",
                results);
    }
}
