package org.example.A_Coordinator;

import org.apache.jena.riot.RIOT;
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.example.A_Coordinator.config.Config.MappingConfig.BERTMAP;
import static org.example.A_Coordinator.config.Config.MappingConfig.EXACT_MAPPER;

public class Pipeline {

    public static Config config;

    public Pipeline(Config config) {
        Pipeline.config = config;
        /* Initialize RDF formats to fix the following error in the uber jar:
            Exception in thread "main" org.apache.jena.shared.NoWriterForLangException: Writer not found: TURTLE
            at org.apache.jena.rdf.model.impl.RDFWriterFImpl.getWriter(RDFWriterFImpl.java:66)
         */
        RIOT.init();
    }

    public void run() {
        // B. Load Data source -----------------------------------------------------------------------------------------
        Object dataSource = getDataSource();
        boolean isTabular = dataSource instanceof RelationalDB;
        // C. Extract PO -----------------------------------------------------------------------------------------
        new POntologyExtractor(dataSource);
        // D. Run Mapper -----------------------------------------------------------------------------------------
        switch (config.DOMap.Mapper){
            case EXACT_MAPPER:
                new ExactMapper(null);
                break;
            case BERTMAP:
                //TODO: call bertmap service here
                String bertmapMappingsFile = "C:/Users/karal/progr/onto_workspace/pythonProject/BertMapMappings.json";
                new MappingSelection(config.Out.POntology, config.Out.DOntology,
                                      bertmapMappingsFile, config.DOMap, dataSource);
                break;
            default:
                config.DOMap.printUnsupportedMapperError();
                break;
        }
        // E. Create Knowledge Graph -----------------------------------------------------------------------------------
        // E1: Create Use Case Ontology --------------------------------------------------------------------------------
        new SetPOasDOextension();

        // E2: Create Full Graph ---------------------------------------------------------------------------------------
        // Tabular dataset in the form of a RelationDB
        if(isTabular) {
            new InsertDataRDB((RelationalDB) dataSource);
            ((RelationalDB) dataSource).closeConnection();  // Close connection to relational DB (SQL)

        // DICOM files that have been transformed to DSON
        }else if (config.In.isDSON()) {
            List<String> processedDSONFolder = findFilesInFolder(config.In.ProcessedDataDir, "json");
            new InsertDataJSON(processedDSONFolder);

        // Plain JSON files that were not processed
        // TODO: In case you want to apply some preprocessing steps to the downloaded json files retrieve the processed data dir instead of the downloaded
        }else if (config.In.isJSON()){
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
        List<String> downloadedFiles = findFilesInFolder(config.In.DownloadedDataDir, config.In.FileExtension);
        if(config.In.isCSV() || config.In.isExcel())
            return getExportedDbSource(downloadedFiles);
        else
            return downloadedFiles;
    }

    private List<String> findFilesInFolder(String folder, String fileExtension){
        try (Stream<Path> walk = Files.walk(Paths.get(folder))) {
            return walk
                    .filter(p -> !Files.isDirectory(p))                                 // Not a directory
                    .map(Path::toString)                                                // Convert path to string
                    .filter(f -> f.endsWith(fileExtension))                             // Check end with
                    .collect(Collectors.toList());

        }catch (IOException e) {
            System.err.println("Data source in folder " + folder + " with extension " + fileExtension + " not found");
            e.printStackTrace();
            return null;
        }
    }

    private Object getExportedDbSource(List<String> filesInFolder) {
        return new TabularFilesReader(filesInFolder).getRelationalDB();
    }


}
