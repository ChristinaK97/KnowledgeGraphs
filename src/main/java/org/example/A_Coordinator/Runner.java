package org.example.A_Coordinator;

import org.example.A_Coordinator.config.Config;
import org.example.B_InputDatasetProcessing.SQLdb.DBSchema;
import org.example.B_InputDatasetProcessing.TableFilesReader;
import org.example.C_POextractor.POntologyExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Runner {

    public static Config config;

    public Runner(Config config) {
        Runner.config = config;
    }

    public void pipeline() {
        Object dataSource = getDataSource();
        new POntologyExtractor(dataSource);
    }

    private Object getDataSource() {
        if(config.In.FileExtension.equals("SQL"))
            return new DBSchema();
        else
            return getFileDataSource();
    }

    private Object getFileDataSource() {
        // find files matched the file extension from folder inputDataSource
        try (Stream<Path> walk = Files.walk(Paths.get(config.In.DownloadedDataDir))) {
            List<String> filesInFolder = walk
                    .filter(p -> !Files.isDirectory(p))                                 // not a directory
                    .map(p -> p.toString())                                             // convert path to string
                    .filter(f -> f.endsWith(config.In.FileExtension))                   // check end with
                    .collect(Collectors.toList());                                      // collect all matched to a List

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
        if(filesInFolder.size() > 1)
            //TODO support multiple files
            System.err.println("Multiple file upload is not supported. Only the first file" + filesInFolder.get(0) + " will be uploaded.");
        return new TableFilesReader(filesInFolder).getAsDBSchema();
    }


}
