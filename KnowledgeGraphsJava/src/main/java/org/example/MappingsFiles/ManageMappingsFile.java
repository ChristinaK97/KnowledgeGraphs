package org.example.MappingsFiles;

import com.google.gson.Gson;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.util.JsonUtil;

import java.io.FileReader;
import java.util.List;

import static org.example.A_Coordinator.Pipeline.config;
import static org.example.util.FileHandler.fileExists;

public class ManageMappingsFile {

    protected MappingsFileTemplate fileTemplate;

    public ManageMappingsFile() {
        fileTemplate = new MappingsFileTemplate();
    }

    public static MappingsFileTemplate readMapJSONasTemplate() {
        try (FileReader reader = new FileReader(config.Out.PO2DO_Mappings)) {
            MappingsFileTemplate mft = new Gson().fromJson(reader, MappingsFileTemplate.class);
            mft.postDeserialization();
            return mft;
        } catch (Exception ex) {
            ex.printStackTrace();}
        return null;
    }

    public static List<Table> readMapJSON() {
        try (FileReader reader = new FileReader(config.Out.PO2DO_Mappings)) {
            MappingsFileTemplate mft = new Gson().fromJson(reader, MappingsFileTemplate.class);
            mft.postDeserialization();
            return mft.getTables();
        } catch (Exception ex) {
            ex.printStackTrace();}
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
