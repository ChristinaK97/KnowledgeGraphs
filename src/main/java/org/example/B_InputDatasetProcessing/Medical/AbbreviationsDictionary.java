package org.example.B_InputDatasetProcessing.Medical;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;

import static org.example.A_Coordinator.Runner.config;
import org.example.util.JsonUtil;
import org.example.util.Annotations;
import org.example.util.DatasetDictionary;

import static org.example.util.Annotations.*;


public class AbbreviationsDictionary extends DatasetDictionary {

    class HeaderInfo extends DatasetElementInfo{
        private ArrayList<String> headerFFs;

        public HeaderInfo(String tokenized, ArrayList<String> headerFFs) {
            elementName = tokenized;
            this.headerFFs = headerFFs;
        }
        protected void addHeaderFF(String headerFF) {
            headerFFs.add(headerFF);
        }

        public ArrayList<String> getHeaderFFs() {
            return headerFFs;
        }

        @Override
        public String toString() {
            return elementName + "\t" + headerFFs;
        }
    }


    public AbbreviationsDictionary() {
        readAbbrevExpansionResults();
        expandResultsToProperties();
    }


    private void readAbbrevExpansionResults() {
        JsonObject results = JsonUtil.readJSON(config.Out.abbrevExpansionResultsFile).getAsJsonObject();
        for (String header : results.keySet()) {
            JsonObject hIJ = results.getAsJsonObject(header);

            String tokenized = hIJ.get("tokenized").getAsString();
            if(normalise(header, false).equals(tokenized))
                tokenized = null;

            HeaderInfo headerInfo = new HeaderInfo(tokenized, new ArrayList<>());
            for (JsonElement headerCand : hIJ.getAsJsonArray("headerCands")) {
                String headerFF = headerCand.getAsJsonObject().get("headerFF").getAsString();
                headerInfo.addHeaderFF(headerFF);
            }
            datasetDictionary.put(header, headerInfo);
        }
    }

    private void expandResultsToProperties() {
        HashMap<String, HeaderInfo> newEntries = new HashMap<>(datasetDictionary.size() * 2);
        datasetDictionary.forEach((header, headerInfo) -> {
            String propertyTokenizedLabel = headerInfo.getElementName();
            if (propertyTokenizedLabel != null)
                propertyTokenizedLabel = "has " + propertyTokenizedLabel;
            ArrayList<String> propertyFFs = new ArrayList<>();
            for(String headerFF : ((HeaderInfo) headerInfo).getHeaderFFs())
                propertyFFs.add("has " + headerFF);

            newEntries.put(getObjectPropertyRawLabel(header), new HeaderInfo(propertyTokenizedLabel, propertyFFs));
            newEntries.put(Annotations.dataPropName(header), new HeaderInfo(propertyTokenizedLabel, propertyFFs));
        });
        datasetDictionary.putAll(newEntries);
    }

    @Override
    public ArrayList<String> getAdditionalAnnotations(String elementCode) {
        return ((HeaderInfo) datasetDictionary.get(elementCode)).getHeaderFFs();
    }
}



















