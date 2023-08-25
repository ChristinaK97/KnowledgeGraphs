package org.example.InputPoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.example.InputPoint.SQLdb.DBSchema;

import javax.swing.*;

public class InputDataSource {

    public static final String resourcePath = "src/main/resources/";
    // input data source

    // uncomment for sql:
    //public static final String inputDataSource = "SQL";
    //public static final String ontologyName = "epibank";

    // uncomment for files:
    public static final String inputDataSource = resourcePath + "dicom_data";
    public static final String fileExtension = "dcm"; //  //"json"; // null; //"csv";
    public static final String ontologyName = inputDataSource.substring(inputDataSource.lastIndexOf("/")+1);

    // sql database sample data
    public static final String SQL_DDL = resourcePath + "EPIBANK_SQL_DDL_MySQL.sql";
    public static final String simulatedDataFull = resourcePath + "simulated_data_v2/";
    public static final String simulatedDataSample = resourcePath + "simulated_data_v2 - sample/";

    // do and po ontologies
    public static final String DOontology = resourcePath + "saved_dicom/dicom.owl";
    public static final boolean offlineDOontology = true;
    public static final String POontology = resourcePath + "POntology.ttl";

    // po to do mappings
    public static final String TableWithMappings = resourcePath + "TableWithMappings.csv";
    public static final String PO2DO_Mappings = resourcePath + "PO2DO_Mappings.json";
    public static final String PO2DO_Mappings_ObjProp = resourcePath + "PO2DO_Mappings_ObjProp.json";

    // output ontology
    public static final String outputOntology = resourcePath + "outputOntology.ttl";
    public static final String mergedOutputOntology = resourcePath + "mergedOutputOntology.ttl";

    // final knowledge graph
    public static final String individualsTTL = resourcePath + "individuals.ttl";
    public static final String pathsTXT = resourcePath + "paths.txt";
    public static final String fullGraph = resourcePath + "fullGraph.ttl";
    public static final String sampleGraph = resourcePath + "sampleGraph.ttl";


    public Object getDataSource() {
        if(inputDataSource.equals("SQL"))
            return new DBSchema();
        else {
            // find files matched the file extension from folder inputDataSource
            try (Stream<Path> walk = Files.walk(Paths.get(inputDataSource))) {
                List<String> filesInFolder = walk
                        .filter(p -> !Files.isDirectory(p))             // not a directory
                        .map(p -> p.toString())                        // convert path to string
                        .filter(f -> f.endsWith(fileExtension))       // check end with
                        .collect(Collectors.toList());               // collect all matched to a List

                if (isSingleTableFile()) {

                    if(filesInFolder.size() > 1)
                        //TODO support multiple files
                        System.err.println("Multiple file upload is not supported. Only the first file" + filesInFolder.get(0) + " will be uploaded.");
                    return new TableFilesReader(filesInFolder.get(0)).getAsDBSchema();
                }else
                    return filesInFolder;

            }catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    public String getSchemaName() {
        return ontologyName;
    }

    public static boolean isJSON() {
        return "json".equals(fileExtension);
    }
    public static boolean isDSON() {
        return "dcm".equals(fileExtension);
    }
    public static boolean isSingleTableFile() {return  isCSV() || isExcel();}
    public static boolean isExcel(){return "xlsx".equals(fileExtension);}
    public static boolean isCSV(){return "csv".equals(fileExtension);}
}
