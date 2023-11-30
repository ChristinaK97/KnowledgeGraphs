package org.example.MappingsFiles;

import com.google.gson.Gson;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.util.JsonUtil;

import java.io.FileReader;
import java.util.List;

import static org.example.A_Coordinator.Pipeline.config;
import static org.example.util.FileHandler.fileExists;

public class ManageMappingsFile {

    protected MappingsFileTemplate mappingsFile;

    /** Create a handler for the mappings json file, containing an instance of the MappingsFileTemplate class
     * @param overrideExistingMappingsFile: If set to false it will retrieve the saved mappings file.
     *        To save this new file, also call saveMappingsFile<br><br>
     *        If set to true (or a saved file doesn't exist), it will create a new MappingsFileTemplate object.
     *        To override the (possibly) existing file with this new mappings file, also call saveMappingsFile after
     *        performing modifications.
     */
    public ManageMappingsFile(boolean overrideExistingMappingsFile) {
        mappingsFile = (overrideExistingMappingsFile || !fileExists(config.Out.PO2DO_Mappings)) ?
                new MappingsFileTemplate()
                : readMappingsFile();
    }

    /** When called as static, it is recommended to be for read only.
     * To also save modifications, create a ManageMappingsFile object, or from one of its subclasses */
    public static MappingsFileTemplate readMappingsFile() {
        try (FileReader reader = new FileReader(config.Out.PO2DO_Mappings)) {
            MappingsFileTemplate mft = new Gson().fromJson(reader, MappingsFileTemplate.class);
            mft.postDeserialization();
            return mft;
        } catch (Exception ex) {
            ex.printStackTrace();}
        return null;
    }

    /** When called as static, it is recommended to be for read only.
     * To also save modifications, create a ManageMappingsFile object, or from one of its subclasses */
    public static List<Table> readTableMappings() {
        try (FileReader reader = new FileReader(config.Out.PO2DO_Mappings)) {
            MappingsFileTemplate mft = new Gson().fromJson(reader, MappingsFileTemplate.class);
            mft.postDeserialization();
            return mft.getTables();
        } catch (Exception ex) {
            ex.printStackTrace();}
        return null;
    }


    public void saveMappingsFile() {
        JsonUtil.saveToJSONFile(config.Out.PO2DO_Mappings, mappingsFile);
    }

}
