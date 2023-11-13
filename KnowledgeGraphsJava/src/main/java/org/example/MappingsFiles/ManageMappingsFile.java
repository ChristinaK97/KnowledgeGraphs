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

    public ManageMappingsFile(boolean resetMappingsFile) {
        resetMappingsFile = resetMappingsFile || !fileExists(config.Out.PO2DO_Mappings);
        System.out.println("Reset mappings file? " + resetMappingsFile);
        fileTemplate = resetMappingsFile ? new MappingsFileTemplate() : readMapJSONasTemplate();
        if(fileTemplate == null) {
            System.out.println("File not found. So reset it.");
            fileTemplate = new MappingsFileTemplate();
        }
    }

    public ManageMappingsFile() {
        fileTemplate = new MappingsFileTemplate();
    }

    public static MappingsFileTemplate readMapJSONasTemplate() {
        try (FileReader reader = new FileReader(config.Out.PO2DO_Mappings)) {
            // Convert JSON file to Java object
            return new Gson().fromJson(reader, MappingsFileTemplate.class);
        } catch (Exception ex) {
            ex.printStackTrace();}
        return null;
    }

    public static List<Table> readMapJSON() {
        try (FileReader reader = new FileReader(config.Out.PO2DO_Mappings)) {
            return new Gson().fromJson(reader, MappingsFileTemplate.class).getTables();
        } catch (Exception ex) {
            ex.printStackTrace();}
        return null;
    }

    //TODO: this is for testing. Remove it
    public static List<Table> readMapJSON(String PO2DOMappingPath) {
        try (FileReader reader = new FileReader(PO2DOMappingPath)) {
            // Convert JSON file to Java object
            return new Gson().fromJson(reader, MappingsFileTemplate.class).getTables();
        } catch (Exception ex) {
            ex.printStackTrace();}
        return null;
    }
    //TODO: for testing. Remove it
    /*public void saveMappingsFile(String PO2DO_Mappings_file) {
        JsonUtil.saveToJSONFile(PO2DO_Mappings_file, fileTemplate);
    }*/


    public void saveMappingsFile(List<Table> tablesList) {
        fileTemplate.setTables(tablesList);
        saveMappingsFile();
    }

    public void saveMappingsFile() {
        JsonUtil.saveToJSONFile(config.Out.PO2DO_Mappings, fileTemplate);
    }

}
