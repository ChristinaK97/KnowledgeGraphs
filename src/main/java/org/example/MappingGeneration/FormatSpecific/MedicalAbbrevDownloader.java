package org.example.MappingGeneration.FormatSpecific;

import org.example.util.JsonUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.TreeMap;


public class MedicalAbbrevDownloader {

    public static final String MEDICAL_ABBREV_FILE = "src/main/resources/medical_data/MedicalAbbreviations.json";

    public static void main(String[] args) {

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
}
