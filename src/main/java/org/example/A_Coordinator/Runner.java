package org.example.A_Coordinator;

import org.example.A_Coordinator.config.Config;
import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.B_InputDatasetProcessing.Tabular.TabularFilesReader;
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
        if(dataSource instanceof RelationalDB)
            ((RelationalDB) dataSource).closeConnection();
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
                    .map(p -> p.toString())                                             // Convert path to string
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
