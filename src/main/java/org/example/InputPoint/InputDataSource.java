package org.example.InputPoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.example.InputPoint.SQLdb.DBSchema;
import org.example.util.Util;

public class InputDataSource {

    // input data source
    public static final String inputDataSource = Util.resourcePath + "dicom";
    public static final String SQL = "SQL";
    public static final String fileExtension = "dcm";
    public static final String ontologyName = inputDataSource.substring(inputDataSource.lastIndexOf("/")+1);

    // sql database sample data
    public static final String SQL_DDL = Util.resourcePath + "EFS_SQL_DDL_MySQL.sql";
    public static final String simulatedDataFull = Util.resourcePath + "simulated_data_v2/";
    public static final String simulatedDataSample = Util.resourcePath + "simulated_data_v2 - sample/";

    public Object getDataSource() {
        if(inputDataSource.equals(SQL))
            return new DBSchema();
        else {
            // find files matched the file extension from folder inputDataSource
            try (Stream<Path> walk = Files.walk(Paths.get(inputDataSource))) {
                return walk
                        .filter(p -> !Files.isDirectory(p))             // not a directory
                        .map(p -> p.toString())                        // convert path to string
                        .filter(f -> f.endsWith(fileExtension))       // check end with
                        .collect(Collectors.toList());               // collect all matched to a List
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

}
