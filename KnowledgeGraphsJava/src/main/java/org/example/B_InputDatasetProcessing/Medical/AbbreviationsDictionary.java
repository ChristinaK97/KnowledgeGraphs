package org.example.B_InputDatasetProcessing.Medical;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.A_Coordinator.config.Config;
import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.D_MappingGeneration.BertMap;
import org.example.util.Annotations;
import org.example.util.DatasetDictionary;
import org.example.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.A_Coordinator.Pipeline.config;
import static org.example.A_Coordinator.config.Config.AAExpansionEndpoint;
import static org.example.util.Annotations.getObjectPropertyRawLabel;
import static org.example.util.Annotations.normalise;


public class AbbreviationsDictionary extends DatasetDictionary {

    Logger LG = LoggerFactory.getLogger(AbbreviationsDictionary.class);

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
                        extractHeaders(db), 0));
        expandResultsToProperties();
    }


    private List<String> extractHeaders(RelationalDB db) {
        // TODO: Now supports only single table dataset
        String tableName = db.getrTables().keySet().iterator().next();
        List<String> headers = db.retrieveDataFromTable(tableName).columnNames();
        return headers;
    }


    private JsonObject startAAExpansion(List<String> inputs, int attempt) {

        LG.info("Making request to " + AAExpansionEndpoint+" . Attempt # " + (attempt+1));
        JsonObject results = startAAExpansion(inputs);
        if(results != null)
            return results;
        else{ // request was unsuccessful, sleep 10'' and try again
            if(attempt < 4) {
                try {Thread.sleep(10000);} catch (InterruptedException ignore) {}
                return startAAExpansion(inputs, ++attempt);
            }else {
                LG.error("AAExpansion service unavailable. Max number of attempts (5) reached. " +
                        "Process will proceed without AAExpansion step.");
                return null;
            }
        }
    }
    // -calls->
    /** HTTP GET Request to AAExpansion service */
    private JsonObject startAAExpansion(List<String> inputs) {
        Logger LG = LoggerFactory.getLogger(AbbreviationsDictionary.class);
        LG.info("HTTP REQUEST: POST " + AAExpansionEndpoint + " # headers = " + inputs.size());
        try {
            // Create a request body containing inputs and useScispacyEntityLinker
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("inputs", inputs);
            requestBody.put("useScispacyEntityLinker", config.Out.useScispacyEntityLinker);

            String responseJson = WebClient.builder()
                    .exchangeStrategies(ExchangeStrategies.builder()
                            .codecs(configurer -> configurer.defaultCodecs()
                                    .maxInMemorySize(16 * 1024 * 1024)) // Set the buffer size to 16 MB
                            .build()
                    ).build()
                    .post()
                    .uri(AAExpansionEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody) // Pass the combined request body
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofHours(64))
                    .block();

            if (responseJson != null) {
                // Parse the JSON response into a JsonObject using GSON
                JsonObject response = JsonParser.parseString(responseJson).getAsJsonObject();
                LG.info("REQUEST SUCCESSFUL. # keys " + response.keySet().size());
                return response;
            } else {
                LG.error("AAExpansion request failed: response was NULL.");
                return null;
            }
        } catch (Exception e) {
            LG.error("AAExpansion request failed: " + e.getLocalizedMessage());
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


