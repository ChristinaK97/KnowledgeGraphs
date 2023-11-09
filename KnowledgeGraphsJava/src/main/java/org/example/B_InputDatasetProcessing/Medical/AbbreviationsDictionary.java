package org.example.B_InputDatasetProcessing.Medical;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.example.A_Coordinator.Inputs.InputConnector.DOCKER_ENV;
import static org.example.A_Coordinator.Pipeline.config;

import com.google.gson.JsonParser;
import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.util.JsonUtil;
import org.example.util.Annotations;
import org.example.util.DatasetDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import static org.example.util.Annotations.*;


public class AbbreviationsDictionary extends DatasetDictionary {

    // Define the URL of the Python service
    private static String AAExpansionEndpoint =
            String.format("http://%s:7531/start_aa_expansion", DOCKER_ENV ? "aa-expansion" : "192.168.1.5");

// =====================================================================================================================
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

// =====================================================================================================================
    /** For reading results from the json file created by the AAExpansion service */
    public AbbreviationsDictionary() {
        readAbbrevExpansionResults();
        expandResultsToProperties();
    }

    private void readAbbrevExpansionResults() {
        JsonObject results = null;
        try {
            results = JsonUtil.readJSON(config.Out.abbrevExpansionResultsFile).getAsJsonObject();
        }catch (Exception ignore) {}
        readAbbrevExpansionResults(results);
    }


// =====================================================================================================================
    /** For calling AAExpansion service with input the headers of the table and output/response the expansion results */
    public AbbreviationsDictionary(RelationalDB db) {
        readAbbrevExpansionResults(
                startAAExpansion(
                        extractHeaders(db)));
        expandResultsToProperties();
    }


    private List<String> extractHeaders(RelationalDB db) {
        // TODO: Now supports only single table dataset
        String tableName = db.getrTables().keySet().iterator().next();
        List<String> headers = db.retrieveDataFromTable(tableName).columnNames();
        return headers;
    }


    /** HTTP GET Request to AAExpansion service */
    private JsonObject startAAExpansion(List<String> inputs) {
        Logger LG = LoggerFactory.getLogger(AbbreviationsDictionary.class);
        LG.info("HTTP REQUEST: GET " + AAExpansionEndpoint);
        // Prepare the HTTP headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Send an HTTP POST request to the Python service
        String responseJson = new RestTemplate().postForObject(
                AAExpansionEndpoint,
                new HttpEntity<>(inputs, headers),
                String.class
        );
        if(responseJson != null) {
            // Parse the JSON response into a JsonObject using GSON
            JsonObject response = JsonParser.parseString(responseJson).getAsJsonObject();
            LG.info("REQUEST SUCCESSFUL");
            System.out.println(response);
            return response;
        }else {
            LG.error("AAExpansion request failed: response was NULL.");
            return null;
        }
    }


// =====================================================================================================================
    private void readAbbrevExpansionResults(JsonObject results) {
        if(results == null) // in case of an error skip the AAExpansion step
            return;

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



















