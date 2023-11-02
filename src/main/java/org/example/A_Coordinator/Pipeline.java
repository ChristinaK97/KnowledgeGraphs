package org.example.A_Coordinator;

import org.example.A_Coordinator.config.Config;
import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.B_InputDatasetProcessing.Tabular.TabularFilesReader;
import org.example.C_POextractor.POntologyExtractor;
import org.example.D_MappingGeneration.ExactMapper;
import org.example.D_MappingGeneration.MappingSelection.MappingSelection;
import org.example.E_CreateKG.InsertDataJSON;
import org.example.E_CreateKG.InsertDataRDB;
import org.example.E_CreateKG.SetPOasDOextension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.example.A_Coordinator.config.Config.MappingConfig.BERTMAP;
import static org.example.A_Coordinator.config.Config.MappingConfig.EXACT_MAPPER;

public class Pipeline {

    public static Config config;

    public Pipeline(Config config) {
        Pipeline.config = config;
    }

    public void run() {
        // B. Load Data source
        Object dataSource = getDataSource();
        boolean isTabular = dataSource instanceof RelationalDB;
        // C. Extract PO
        new POntologyExtractor(dataSource);
        // D. Run Mapper
        switch (config.DOMap.Mapper){
            case EXACT_MAPPER:
                new ExactMapper(null);
                break;
            case BERTMAP:
                String bertmapMappingsFile = "C:/Users/karal/progr/onto_workspace/pythonProject/BertMapMappings.json";
                new MappingSelection(config.Out.POntology, config.Out.DOntology,
                                      bertmapMappingsFile, config.DOMap, dataSource);
                break;
            default:
                config.DOMap.printUnsupportedMapperError();
                break;
        }
        // E. Create Knowledge Graph
        // E1: Create Use Case Ontology
        new SetPOasDOextension();
        // E2: Create Full Graph
        if(isTabular) {
            new InsertDataRDB((RelationalDB) dataSource);
            // Close connection to relational DB (SQL)
            ((RelationalDB) dataSource).closeConnection();
        }else {
            new InsertDataJSON((List<String>) dataSource);
        }

    }

    private Object getDataSource() {
        if(config.In.FileExtension.equals("SQL"))
            return new RelationalDB();
        else
            return getFileDataSource();
    }

    private Object getFileDataSource() {
        try (Stream<Path> walk = Files.walk(Paths.get(config.In.DownloadedDataDir))) {
            List<String> filesInFolder = walk
                    .filter(p -> !Files.isDirectory(p))                                 // Not a directory
                    .map(Path::toString)                                                // Convert path to string
                    .filter(f -> f.endsWith(config.In.FileExtension))                   // Check end with
                    .collect(Collectors.toList());

            if(config.In.isCSV() || config.In.isExcel()) {
                return getExportedDbSource(filesInFolder);
            }else
                return filesInFolder;

        }catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Object getExportedDbSource(List<String> filesInFolder) {
        return new TabularFilesReader(filesInFolder).getRelationalDB();
    }


}
