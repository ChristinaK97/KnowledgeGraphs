package org.example.MappingsFiles;

import com.google.gson.Gson;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.B_InputDatasetProcessing.JsonUtil;

import java.io.FileReader;
import java.util.List;

import static org.example.A_Coordinator.Runner.config;

public class ManageMappingsFile {

    protected MappingsFileTemplate fileTemplate;

    public ManageMappingsFile() {
        fileTemplate = new MappingsFileTemplate();
    }

    public static List<Table> readMapJSON() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(config.Out.PO2DO_Mappings)) {
            // Convert JSON file to Java object
            return gson.fromJson(reader, MappingsFileTemplate.class).getTables();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }


    public void saveMappingsFile(List<Table> tablesList) {
        fileTemplate.setTables(tablesList);
        saveMappingsFile();
    }

    public void saveMappingsFile() {
        JsonUtil.saveToJSONFile(config.Out.PO2DO_Mappings, fileTemplate);
    }


}
