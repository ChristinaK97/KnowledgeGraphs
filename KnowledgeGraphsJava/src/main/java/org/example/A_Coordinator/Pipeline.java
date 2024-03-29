package org.example.A_Coordinator;

import com.google.gson.JsonObject;
import org.apache.jena.riot.RIOT;
import org.example.A_Coordinator.Kafka.KafkaProducerService;
import org.example.A_Coordinator.config.Config;
import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.B_InputDatasetProcessing.Tabular.TabularFilesReader;
import org.example.C_POextractor.POntologyExtractor;
import org.example.D_MappingGeneration.BertMap;
import org.example.D_MappingGeneration.ExactMapper;
import org.example.D_MappingGeneration.MappingSelection.MappingSelection;
import org.example.E_CreateKG.GraphDB;
import org.example.E_CreateKG.InsertDataJSON;
import org.example.E_CreateKG.InsertDataRDB;
import org.example.E_CreateKG.SetPOasDOextension;
import org.example.F_PII.PIIidentification;
import org.example.F_PII.PIIresultsTemplate;
import org.example.util.Ontology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

import static org.example.A_Coordinator.config.Config.MappingConfig.BERTMAP;
import static org.example.A_Coordinator.config.Config.MappingConfig.EXACT_MAPPER;
import static org.example.util.FileHandler.findFilesInFolder;

public class Pipeline {

    public static Config config;
    private KafkaProducerService kafkaProducerService;
    private static Logger LG = LoggerFactory.getLogger(Pipeline.class);

    public Pipeline(Config config, KafkaProducerService kafkaProducerService) {
        Pipeline.config = config;
        this.kafkaProducerService = kafkaProducerService;
        /* Initialize RDF formats to fix the following error in the uber jar:
            Exception in thread "main" org.apache.jena.shared.NoWriterForLangException: Writer not found: TURTLE
            at org.apache.jena.rdf.model.impl.RDFWriterFImpl.getWriter(RDFWriterFImpl.java:66)
         */
        RIOT.init();
    }

    public void run() {
        Ontology cachedOnto = null;
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
                cachedOnto = new Ontology(config.DOMap.TgtOntology);     // the domain ontology, fibo snomed etc
                new ExactMapper(cachedOnto, null);
            }
            case BERTMAP -> {
                JsonObject bertmapResults = new BertMap().startBertmap(/* mapToDo ? */ true); //Calls bertmap service
                cachedOnto = new Ontology(config.DOMap.TgtOntology);   // the domain ontology, fibo snomed etc   
                new MappingSelection(
                    config.Out.POntology,          //=src_onto
                    cachedOnto,                   //=tgt_onto
                    bertmapResults,              //=bertmapJson. Calls bertmap service
                    config.DOMap,               //=mapping config params
                    dataSource,                //=input data source = */
                    true
                );
            }
            default ->
                    config.DOMap.printUnsupportedMapperError();
        }
        // E. Create Knowledge Graph -----------------------------------------------------------------------------------
        LG.info("E1. CREATE USE CASE ONTOLOGY");
        cachedOnto = new SetPOasDOextension(cachedOnto).getRefinedOntology();   // the refined ontology: DO+PO

        // -------------------------------------------------------------------------------------------------------------
        LG.info("E2: CREATE FULL GRAPH");
        // Tabular dataset in the form of a RelationDB -----------------------------------------------------------------
        if(isTabular) {
            new InsertDataRDB((RelationalDB) dataSource, cachedOnto);
            ((RelationalDB) dataSource).closeConnection();  // Close connection to relational DB (SQL)

        // DICOM files that have been transformed to DSON --------------------------------------------------------------
        }else if (config.In.isDSON()) {
            List<String> processedDSONFolder = findFilesInFolder(config.In.ProcessedDataDir, "json");
            new InsertDataJSON(processedDSONFolder, cachedOnto);

        // Plain JSON files that were not processed --------------------------------------------------------------------
        // TODO: In case you want to apply some preprocessing steps to the downloaded json files retrieve the processed data dir instead of the downloaded
        }else if (config.In.isJSON()){
            new InsertDataJSON((List<String>) dataSource, cachedOnto);
        }
        cachedOnto = null;   // refined ontology is no longer needed

        LG.info("F. PII IDENTIFICATION");
        runPiiIdentificationPipeline();

        // upload kg to graphdb after having sent the piis
        if(config.Out.uploadToGraphDB) {
            LG.info("UPLOAD KG TO GRAPHDB");
            new GraphDB(/*reset repository ? */ true);
        }else
            LG.info("Upload to GraphDB was skipped since the arg uploadToGraphDB in the configuration file is set to false");
    }


    private void runPiiIdentificationPipeline() {
        new MappingSelection(
            config.Out.POntology,              //=src_onto
            config.PiiMap.TgtOntology,         //=tgt_onto
            new BertMap().startBertmap(/* mapToDo ? */ false), //=bertmapJson. Calls bertmap service
            config.PiiMap,                      //=mapping config params
            null,                              //=datasource not needed for pii mapping
            false                             //=mapToDo ?
        );
        PIIresultsTemplate PIIResults = new PIIidentification().getPiiResults();
        LG.info("PII IDENTIFICATION FINISHED. PRODUCING KAFKA MESSAGE...");
        kafkaProducerService.sendMessage(PIIResults);
    }


    private Object getDataSource() {
        if(config.In.FileExtension.equals("SQL"))
            return new RelationalDB();
        else
            return getFileDataSource();
    }

    private Object getFileDataSource() {
        List<String> downloadedFiles = findFilesInFolder(config.In.DownloadedDataDir, config.In.FileExtension);
        if(config.In.isCSVlike())
            return getExportedDbSource(downloadedFiles);
        else
            return downloadedFiles;
    }



    private Object getExportedDbSource(List<String> filesInFolder) {
        return new TabularFilesReader(filesInFolder).getRelationalDB();
    }


}
