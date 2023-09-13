package org.example.temp.BertMapCheckMappings;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.temp.BertMapCheckMappings.BertMapMapping.Column;
import org.example.temp.BertMapCheckMappings.BertMapMapping.Mapping;
import org.example.temp.BertMapCheckMappings.BertMapMapping.Table;
import org.example.InputPoint.JsonUtil;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BertMapMappingsWrite {

    HashMap<String, ArrayList<String>> bertmapResults = new HashMap<>();
    static String bertmap_results_json = "src/main/resources/Bertmap results fintech/max avg/bertmaplogfile efs2fibo updated with low score mappings max.json";
    static String mappingFileJson = "src/main/resources/saved_efs/EFS_mappings.json";
    static String outputFile = "src/main/resources/saved_efs/bertmap_fintech.json";

    public BertMapMappingsWrite() {
        readBertmapMappings();
        saveMappingsFile(
            writeBertmapResults()
        );
    }
    public BertMapMappingsWrite(boolean doNothingConstructor) {}

    public HashMap<String, ArrayList<String>> readBertmapMappings() {
        JsonElement bertmapJson = JsonUtil.readJSON(bertmap_results_json);

        if (bertmapJson.isJsonObject()) {
            JsonObject jsonObject = bertmapJson.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String key = entry.getKey();
                JsonArray jsonArray = entry.getValue().getAsJsonArray();
                ArrayList<String> values = new ArrayList<>();

                for (JsonElement element : jsonArray) {
                    JsonArray innerArray = element.getAsJsonArray();
                    if (innerArray.size() >= 2) {
                        String uri = innerArray.get(1).getAsString();
                        String score = innerArray.get(2).getAsString();
                        String rank = innerArray.get(3).getAsString();
                        values.add(uri);
                        values.add(score);
                        values.add(rank);
                    }
                }

                if (!values.isEmpty()) {
                    bertmapResults.put(key, values);
                }
            }
        }
        return bertmapResults;
    }

    private BertMapMapping writeBertmapResults() {
        BertMapMapping manualMappings = readMapJSON(mappingFileJson);
        assert manualMappings != null;
        for(Table tableMaps : manualMappings.getTables()) {
            Mapping tableMap = tableMaps.getMapping();
            setMatch(tableMap);

            for (Column col : tableMaps.getColumns()) {
                setMatch(col.getObjectPropMapping());
                setMatch(col.getClassPropMapping());
                setMatch(col.getDataPropMapping());
            }
        }
        return manualMappings;
    }

    private void setMatch(Mapping map) {
        String ontoEl = map.getOntoElURI().toString();
        if(bertmapResults.containsKey(ontoEl)) {
            map.setBertmap(bertmapResults.get(ontoEl));
        }
    }

    public static BertMapMapping readMapJSON(String customFile) {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(customFile)) {
            // Convert JSON file to Java object
            return gson.fromJson(reader, BertMapMapping.class);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public void saveMappingsFile(BertMapMapping mappings) {
        JsonUtil.saveToJSONFile(outputFile, mappings);
    }

    public static void main(String[] args) {
        new BertMapMappingsWrite();
    }

}


