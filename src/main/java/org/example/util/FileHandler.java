package org.example.util;

import java.nio.file.Paths;

import static org.example.A_Coordinator.Runner.config;

public class FileHandler {
    public static String getFileName(String file) {
        return Paths.get(file).getFileName().toString();
    }

    public static String getProcessedFileSubfolder(String file, String fileLocalName) {
        return config.In.ProcessedDataDir +
                file.substring(config.In.DownloadedDataDir.length(), file.indexOf(fileLocalName)-1);
    }

    public static String getProcessedFilePath(String downloadedFilePath) {
        String fileLocalName = getFileName(downloadedFilePath);
        String fileSubFolder = getProcessedFileSubfolder(downloadedFilePath, fileLocalName);
        return String.format("%s/%s", fileSubFolder, fileLocalName);
    }
}
