package org.example.A_Coordinator.config;

import com.google.gson.JsonObject;
import org.example.util.JsonUtil;

import java.nio.file.Paths;

public class Config {

    public static String FINTECH = "Fintech";
    public static String HEATH = "Health";
    public static String CTI = "CTI";
    public static String resourcePath = "src/main/resources/";
    public InputPointConfig In;
    public KGOutputsConfig Out;


    public Config(String UseCase, String FileExtension) {
        setConfigParams(UseCase, FileExtension);
    }


    private void setConfigParams(String UseCase, String FileExtension) {

        String configFilePath = String.format("%sConfigFiles/%s_%s_Config.json", resourcePath, UseCase, FileExtension);
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
            DatasetResourcesPath = String.format("%sUse_Case/%s/%s/", resourcePath, UseCase, DatasetName);
            DownloadedDataDir    = DatasetResourcesPath + "Downloaded_Data/";                                            // overrideDatasource == null ? DatasetResourcesPath + "Downloaded_Data/" : overrideDatasource;
            ProcessedDataDir     = DatasetResourcesPath + "Processed_Data/";
            CachedDataDir        = DatasetResourcesPath + "Cached_Data/";
        }

        public boolean isJSON() {
            return "json".equals(FileExtension);
        }
        public boolean isDSON() {
            return "dcm".equals(FileExtension);
        }
        public boolean isExcel(){return "xlsx".equals(FileExtension);}
        public  boolean isCSV(){return "csv".equals(FileExtension);}
        public boolean isSQL(){return credentials != null;}

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
            abbrevExpansionResultsFile = String.format("%sOther/abbrevExpansionResults.json", DatasetResourcesPath);

            KGOutputsDir    = String.format("%sKG_Outputs/", DatasetResourcesPath);
            POntologyName   = DatasetName;
            POntology       = KGOutputsDir + "POntology.ttl";
            POntologyBaseNS = String.format("http://www.example.net/ontologies/%s.owl/", POntologyName);

            this.DefaultRootClassName = DefaultRootClassName;

            String DOdir = Paths.get(DatasetResourcesPath).getParent().toString().replace("\\","/");
            this.DOntology = String.format("%sDOntology/%s", DOdir, DOntology);
            this.offlineDOntology = offlineDOntology;

            PO2DO_Mappings  = KGOutputsDir + "PO2DO_Mappings.json";
            RefinedOntology = KGOutputsDir + "refinedOntology.ttl";
            IndividualsTTL  = KGOutputsDir + "individuals.ttl";
            FullGraph       = KGOutputsDir + "fullGraph.ttl";
            LogDir          = DatasetResourcesPath + "Log/";
            PathsTXT        = LogDir + "paths.txt";
        }
    }


}


/* if(FileExtension == null) {
       overrideDatasource = datasetFolderParams.get("DownloadedDataDir").getAsString();
       FileExtension = datasetFolderParams.get("FileExtension").getAsString();
}*/