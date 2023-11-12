package org.example.A_Coordinator;

import com.google.gson.JsonObject;
import org.apache.jena.riot.RIOT;
import org.example.A_Coordinator.config.Config;
import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.B_InputDatasetProcessing.Tabular.TabularFilesReader;
import org.example.C_POextractor.POntologyExtractor;
import org.example.D_MappingGeneration.BertMap;
import org.example.D_MappingGeneration.ExactMapper;
import org.example.D_MappingGeneration.MappingSelection.MappingSelection;
import org.example.E_CreateKG.InsertDataJSON;
import org.example.E_CreateKG.InsertDataRDB;
import org.example.E_CreateKG.SetPOasDOextension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static Logger LG = LoggerFactory.getLogger(Pipeline.class);

    public Pipeline(Config config) {
        Pipeline.config = config;
        /* Initialize RDF formats to fix the following error in the uber jar:
            Exception in thread "main" org.apache.jena.shared.NoWriterForLangException: Writer not found: TURTLE
            at org.apache.jena.rdf.model.impl.RDFWriterFImpl.getWriter(RDFWriterFImpl.java:66)
         */
        RIOT.init();
    }

    public void run() {
        // -------------------------------------------------------------------------------------------------------------
        LG.info("B. LOAD DATA SOURCE");
        Object dataSource = getDataSource();
        boolean isTabular = dataSource instanceof RelationalDB;

        // -------------------------------------------------------------------------------------------------------------
        LG.info("C. EXTRACT PONTOLOGY");
        new POntologyExtractor(dataSource);

        // -------------------------------------------------------------------------------------------------------------
        LG.info("D. RUN MAPPER: " + config.DOMap.Mapper);
        switch (config.DOMap.Mapper) {
            case EXACT_MAPPER -> {
                LG.info("D. RUN EXACT MAPPER");
                new ExactMapper(null);
            }
            case BERTMAP ->
                    new MappingSelection(
                        config.Out.POntology,
                        config.DOMap.TgtOntology,
                        new BertMap().startBertmap(true),
                        config.DOMap, dataSource);
            default ->
                    config.DOMap.printUnsupportedMapperError();
        }
        // E. Create Knowledge Graph -----------------------------------------------------------------------------------
        LG.info("E1. CREATE USE CASE ONTOLOGY");
        new SetPOasDOextension();

        // -------------------------------------------------------------------------------------------------------------
        LG.info("E2: CREATE FULL GRAPH");
        // Tabular dataset in the form of a RelationDB -----------------------------------------------------------------
        if(isTabular) {
            new InsertDataRDB((RelationalDB) dataSource);
            ((RelationalDB) dataSource).closeConnection();  // Close connection to relational DB (SQL)

        // DICOM files that have been transformed to DSON --------------------------------------------------------------
        }else if (config.In.isDSON()) {
            List<String> processedDSONFolder = findFilesInFolder(config.In.ProcessedDataDir, "json");
            new InsertDataJSON(processedDSONFolder);

        // Plain JSON files that were not processed --------------------------------------------------------------------
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
            LG.error("Data source in folder " + folder + " with extension " + fileExtension + " not found");
            e.printStackTrace();
            return null;
        }
    }

    private Object getExportedDbSource(List<String> filesInFolder) {
        return new TabularFilesReader(filesInFolder).getRelationalDB();
    }


}
