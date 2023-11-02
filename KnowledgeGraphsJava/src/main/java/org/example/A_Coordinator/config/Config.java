package org.example.A_Coordinator.config;

import com.google.gson.JsonObject;
import org.example.util.JsonUtil;

import java.nio.file.Paths;

import static org.example.util.FileHandler.getPath;

public class Config {

    public static String FINTECH = "Fintech";
    public static String HEALTH = "Health";
    public static String CTI = "CTI";

    public static String resourcePath = getPath("src/main/resources");
    public InputPointConfig In;
    public KGOutputsConfig Out;
    public MappingConfig DOMap;
    public MappingConfig PiiMap;


    public Config(String UseCase, String FileExtension) {
        setConfigParams(UseCase, FileExtension);
    }


    private void setConfigParams(String UseCase, String FileExtension) {

        String configFilePath = getPath(String.format("%s/ConfigFiles/%s_%s_Config.json", resourcePath, UseCase, FileExtension));
        JsonObject configFile = JsonUtil.readJSON(configFilePath).getAsJsonObject();
        String DefaultRootClassName = "Record";

        // Inputs parameters -----------------------------------------------------------------------

        In = new InputPointConfig();
        JsonObject inputDatasetParams = configFile.getAsJsonObject("InputDataset");

        if("SQL".equals(FileExtension)) {
            JsonObject sqlCredParams = inputDatasetParams.getAsJsonObject("SQL");
            In.setSQLCredentials(
                    sqlCredParams.get("URL").getAsString(),
                    sqlCredParams.get("User").getAsString(),
                    sqlCredParams.get("Password").getAsString());
        }else{
            JsonObject datasetFolderParams = inputDatasetParams.getAsJsonObject("FilesDataset");
            try {
                DefaultRootClassName = datasetFolderParams.get("DefaultRootClassName").getAsString();
            }catch (UnsupportedOperationException ignore) {}
        }
        In.setRequiredParams(UseCase, inputDatasetParams.get("DatasetName").getAsString(), FileExtension);


        // KG outputs parameters -----------------------------------------------------------------------
        JsonObject kgOutsParams = configFile.getAsJsonObject("KnowledgeGraphsOutputs");
        Out = new KGOutputsConfig(In.DatasetName, In.DatasetResourcesPath,
                kgOutsParams.get("applyMedicalAbbreviationExpansionStep").getAsBoolean(),
                kgOutsParams.get("DOntology").getAsString(),
                kgOutsParams.get("offlineDOntology").getAsBoolean(),
                kgOutsParams.get("turnAttributesToClasses").getAsBoolean(),
                kgOutsParams.get("includeInverseAxiom").getAsBoolean(),
                DefaultRootClassName
        );

        // Mapping parameters ------------------------------------------------------------------------------
        JsonObject mappingParams = configFile.getAsJsonObject("DOMappingParameters");
        DOMap = new MappingConfig(mappingParams);

    }



    public static class SQLCredentials {
        public String URL;
        public String User;
        public String Password;
        public SQLCredentials(String URL, String User, String Password) {
            this.URL = URL;
            this.User = User;
            this.Password = Password;
        }
    }

    public static class InputPointConfig {
        // input data source
        public String UseCase;
        public String DatasetName;
        public String DatasetResourcesPath;

        public SQLCredentials credentials;

        public String FileExtension;
        public String DownloadedDataDir;
        public String ProcessedDataDir;
        public String CachedDataDir;

        public InputPointConfig() {}

        public void setRequiredParams(String UseCase, String DatasetName, String FileExtension) {                        // , String overrideDatasource
            this.UseCase = UseCase;
            this.DatasetName = DatasetName;
            this.FileExtension = FileExtension;
            setDirPaths();
        }

        protected void setSQLCredentials(String URL, String User, String Password) {
            this.credentials = new SQLCredentials(URL, User, Password);
        }


        private void setDirPaths() {                                                                                    // String overrideDatasource
            DatasetResourcesPath = getPath(String.format("%s/Use_Case/%s/%s", resourcePath, UseCase, DatasetName));
            DownloadedDataDir    = getPath(DatasetResourcesPath + "/Downloaded_Data");                                            // overrideDatasource == null ? DatasetResourcesPath + "Downloaded_Data/" : overrideDatasource;
            ProcessedDataDir     = getPath(DatasetResourcesPath + "/Processed_Data");
            CachedDataDir        = getPath(DatasetResourcesPath + "/Cached_Data");
        }

        public boolean isJSON() {return "json".equals(FileExtension);}
        public boolean isDSON() {return "dcm".equals(FileExtension);}
        public boolean isExcel(){return "xlsx".equals(FileExtension);}
        public  boolean isCSV() {return "csv".equals(FileExtension);}
        public boolean isSQL()  {return credentials != null;}

    }

    public static class KGOutputsConfig {
        public String KGOutputsDir;
        // preprocessing
        public boolean applyMedAbbrevExpansion;
        public String abbrevExpansionResultsFile;

        // po extraction
        public String POntologyName;
        public String POntology;
        public String POntologyBaseNS;
        public String DefaultRootClassName;
        public String RootClassName;
        public boolean turnAttributesToClasses;
        public boolean includeInverseAxioms;

        // do ontology
        public String DOntology;
        public boolean offlineDOntology;

        // po to do mappings
        public String PO2DO_Mappings;

        // output ontology
        public String RefinedOntology;

        // final knowledge graph
        public String IndividualsTTL;
        public String PathsTXT;
        public String FullGraph;

        public String LogDir;

        public KGOutputsConfig(String DatasetName, String DatasetResourcesPath,
                  boolean applyMedAbbrevExpansion, String DOntology, boolean offlineDOntology,
                  boolean turnAttributesToClasses, boolean includeInverseAxioms,
                  String DefaultRootClassName
        ){
            this.turnAttributesToClasses = turnAttributesToClasses;
            this.includeInverseAxioms = includeInverseAxioms;

            this.applyMedAbbrevExpansion = applyMedAbbrevExpansion;
            abbrevExpansionResultsFile = getPath(String.format("%sOther/abbrevExpansionResults.json", DatasetResourcesPath));

            KGOutputsDir    = getPath(String.format("%s/KG_Outputs/", DatasetResourcesPath));
            POntologyName   = DatasetName;
            POntology       = getPath(KGOutputsDir + "/POntology.ttl");
            POntologyBaseNS = String.format("http://www.example.net/ontologies/%s.owl/", POntologyName);

            this.DefaultRootClassName = DefaultRootClassName;

            String DOdir   = getPath(Paths.get(DatasetResourcesPath).getParent().toString());
            this.DOntology = getPath(String.format("%s/DOntology/%s", DOdir, DOntology));
            this.offlineDOntology = offlineDOntology;

            PO2DO_Mappings  = getPath(KGOutputsDir + "/PO2DO_Mappings.json");
            RefinedOntology = getPath(KGOutputsDir + "/refinedOntology.ttl");
            IndividualsTTL  = getPath(KGOutputsDir + "/individuals.ttl");
            FullGraph       = getPath(KGOutputsDir + "/fullGraph.ttl");
            LogDir          = getPath(DatasetResourcesPath + "/Log");
            PathsTXT        = getPath(LogDir + "/paths.txt");
        }
    }

    public static class MappingConfig {
        public static final String EXACT_MAPPER = "ExactMapper";
        public static final String BERTMAP = "BERTMap";
        public String Mapper;
        public double BES_HIGH_THRS;
        public double BES_LOW_THRS;
        public double PJ_HIGH_THRS;

        public double PJ_REJECT_THRS;
        public double BES_REJECT_THRS;

        public int DEPTH_THRS;
        public boolean rejectPropertyMaps;

        public MappingConfig(JsonObject params) {
            Mapper = params.get("Mapper").getAsString();
            BES_HIGH_THRS   = params.get("BES_HIGH_THRS").getAsDouble();
            BES_LOW_THRS    = params.get("BES_LOW_THRS").getAsDouble();
            PJ_HIGH_THRS    = params.get("PJ_HIGH_THRS").getAsDouble();
            PJ_REJECT_THRS  = params.get("PJ_REJECT_THRS").getAsDouble();
            BES_REJECT_THRS = params.get("BES_REJECT_THRS").getAsDouble();
            DEPTH_THRS      = params.get("DEPTH_THRS").getAsInt();
            rejectPropertyMaps = params.get("rejectPropertyMaps").getAsBoolean();
        }

        public void printUnsupportedMapperError() {
            System.err.printf("Unsupported mapper %s. Choose %s or %s\n", Mapper, EXACT_MAPPER, BERTMAP);
        }
    }
}


/* if(FileExtension == null) {
       overrideDatasource = datasetFolderParams.get("DownloadedDataDir").getAsString();
       FileExtension = datasetFolderParams.get("FileExtension").getAsString();
}*/