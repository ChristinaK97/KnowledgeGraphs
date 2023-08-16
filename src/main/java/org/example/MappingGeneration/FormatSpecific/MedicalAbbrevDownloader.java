package org.example.MappingGeneration.FormatSpecific;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.example.util.JsonUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;


public class MedicalAbbrevDownloader {

    public static final String MEDICAL_ABBREV_FILE =
            "src/main/resources/medical_data/MedicalAbbreviations.json";

    public static HashMap<String, ArrayList<String>> readMedicalAbbrevFile() {
        HashMap<String, ArrayList<String>> medAbbrevMap = new HashMap<>();
        JsonElement json = JsonUtil.readJSON(MEDICAL_ABBREV_FILE);
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();

            // Iterate through the JSON object's entries
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String abbreviation = entry.getKey();
                JsonArray meaningsArray = entry.getValue().getAsJsonArray();

                ArrayList<String> meanings = new ArrayList<>();
                for (JsonElement element : meaningsArray)
                    meanings.add(element.getAsString());
                medAbbrevMap.put(abbreviation, meanings);
            }
        }
        System.out.println(medAbbrevMap);
        return medAbbrevMap;
    }

    public static void downloadWikipediaAbbreviations() {

        TreeMap<String, String[]> abbreviations = new TreeMap<>();

        for (char ch = 'A'; ch <= 'Z'; ch++) {
            String url = "https://en.wikipedia.org/wiki/List_of_medical_abbreviations:_" + ch;

            try {
                Document document = Jsoup.connect(url).get();
                Element table = document.selectFirst(".wikitable");

                Elements rows = table.select("tr");
                for (int i = 1; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements cols = row.select("td");

                    if (cols.size() >= 2) {
                        String abbreviation = cols.get(0).text();
                        String[] meanings = cols.get(1).text().split("\n");

                        abbreviations.put(abbreviation, meanings);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        JsonUtil.saveToJSONFile(MEDICAL_ABBREV_FILE, abbreviations);
    }

    public static void sortJSONbyKey() {
        try {
            // Read JSON file
            JsonElement json = JsonUtil.readJSON(MEDICAL_ABBREV_FILE);

            // Convert JsonElement to Map
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> jsonMap = new Gson().fromJson(json, type);

            // Convert Map to TreeMap (case-insensitive sorting by keys)
            TreeMap<String, Object> sortedJson = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            sortedJson.putAll(jsonMap);

            // Write sorted JSON back to the file
            JsonUtil.saveToJSONFile(MEDICAL_ABBREV_FILE, sortedJson);

            System.out.println("File updated successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
