package org.example.temp.BertMapCheckMappings;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.temp.BertMapCheckMappings.BertMapMapping.Column;
import org.example.temp.BertMapCheckMappings.BertMapMapping.Mapping;
import org.example.temp.BertMapCheckMappings.BertMapMapping.Table;
import org.example.util.JsonUtil;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BertMapMappingsWrite {

    HashMap<String, ArrayList<String>> bertmapResults = new HashMap<>();
    static String bertmap_results_json = "src/main/resources/Bertmap results/Bertmap results fintech/max avg/bertmap_raw_epibank2fibo_updated with low score mappings max.json";
    static String mappingFileJson = "src/main/resources/PO2DO_Mappings.json";
    static String outputFile = "src/main/resources/saved_epibank/bertmap_fintech.json";

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

    public static void extractToTxt() throws FileNotFoundException {
        PrintWriter wr = new PrintWriter("src/main/resources/saved_epibank/bertmap_fintech_extract.txt");
        BertMapMapping results = readMapJSON(outputFile);
        System.out.print(results.getTables().size());
        for(Table table : results.getTables()) {
            System.out.print(">> " + table.getTable() + " :");
            List<String> map = table.getMapping().getBertmap();
            if(map != null)
                for(int i=0; i< map.size()-2 ; i+=3)
                    System.out.print(String.format("\n\t%s\t\t\t(%.3f / %s)", map.get(i).substring(map.get(i).lastIndexOf("/")+1), Double.parseDouble(map.get(i+1))*100, map.get(i+2)));
            System.out.print("\n------------\n");
            for(Column col : table.getColumns()) {
                System.out.print(String.format("\n\t> %s\n\n\tOP\n", col.getColumn()));
                map = col.getObjectPropMapping().getBertmap();
                if (map != null)
                    for(int i=0; i< map.size()-2 ; i+=3)
                        System.out.print(String.format("\t\t-[ %s ]->\t\t\t(%.3f / %s)\n", map.get(i).substring(map.get(i).lastIndexOf("/")+1), Double.parseDouble(map.get(i+1))*100, map.get(i+2)));
                System.out.print("\n\tCL\n");
                map = col.getClassPropMapping().getBertmap();
                if (map != null)
                    for(int i=0; i< map.size()-2 ; i+=3)
                        System.out.print(String.format("\t\t%s\t\t\t(%.3f / %s)\n", map.get(i).substring(map.get(i).lastIndexOf("/")+1), Double.parseDouble(map.get(i+1))*100, map.get(i+2)));
                System.out.print("\n\tDP\n");
                map = col.getDataPropMapping().getBertmap();
                if (map != null)
                    for(int i=0; i< map.size()-2 ; i+=3)
                        System.out.print(String.format("\t\t-[ %s ]->\t\t\t(%.3f / %s)\n", map.get(i).substring(map.get(i).lastIndexOf("/")+1), Double.parseDouble(map.get(i+1))*100, map.get(i+2)));
                System.out.print("\n------------\n");
            }
            System.out.print("============================================================================================\n\n");
        }
        wr.close();

    }

    public void saveMappingsFile(BertMapMapping mappings) {
        JsonUtil.saveToJSONFile(outputFile, mappings);
    }

    public static void main(String[] args) throws FileNotFoundException {
        new BertMapMappingsWrite();
        //BertMapMappingsWrite.extractToTxt();
    }

}


