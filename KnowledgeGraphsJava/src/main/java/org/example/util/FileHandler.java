package org.example.util;

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.example.A_Coordinator.Pipeline.config;

public class FileHandler {

    public static String getFileExtension(String filePath) {
        return filePath.substring(filePath.lastIndexOf(".")+1);
    }

    public static String getFileNameWithExtension(String filePath) {
        return Paths.get(filePath).getFileName().toString();
    }

    public static String getFileNameWithoutExtension(String filePath) {
        return removeExtension(/*fileNameWithExtension = */ getFileNameWithExtension(filePath));
    }

    public static String removeExtension(String fileNameWithExtension) {
        return fileNameWithExtension.substring(0, fileNameWithExtension.lastIndexOf("."));
    }

    public static String getProcessedFileName(String downloadedFilePath, String processedFileExtension) {
        return String.format( "%s.%s",
                getFileNameWithoutExtension(/*downloadedFileName = */ downloadedFilePath),
                processedFileExtension
        );
    }

    public static String getProcessedFileSubfolder(String downloadedFilePath, String fileName) {
        int beginIndex = config.In.DownloadedDataDir.length();
        int endIndex   = downloadedFilePath.indexOf(removeExtension(fileName));
        return getPath(config.In.ProcessedDataDir + downloadedFilePath.substring(beginIndex, endIndex));
    }

    public static String getProcessedFilePath(String downloadedFilePath, String processedFileExtension) {
        String processedFileName = getProcessedFileName(downloadedFilePath, processedFileExtension);
        String processedFileSubfolder = getProcessedFileSubfolder(downloadedFilePath, processedFileName);
        return getPath(String.format("%s/%s", processedFileSubfolder, processedFileName));
    }

    /**
     * Call when saving a processed file
     * @param downloadedFilePath The path of the file downloaded from the api (in the Downloaded_Data dir
     *                           of the dataset. Might be a subfolder)
     * @return The path of the processed file (in the Processed_Data dir. Might be a subfolder that will
     *         have the same name as the subfolder in the original Downloaded_Data subfolder)
     */
    public static String getProcessedFilePath(String downloadedFilePath, String processedFileExtension,
                                              boolean createProcessedSubdir)
    {
        String processedFileName = getProcessedFileName(downloadedFilePath, processedFileExtension);
        String processedFileSubfolder = getProcessedFileSubfolder(downloadedFilePath, processedFileName);

        if(createProcessedSubdir) {
            Path fileSubFolderPath = Paths.get(processedFileSubfolder);
            if (Files.notExists(fileSubFolderPath)) {
                try {
                    Files.createDirectories(fileSubFolderPath);
                } catch (IOException ignore) {}
        }}
        return getPath(String.format("%s/%s", processedFileSubfolder, processedFileName));
    }

    public static String getPath(String fPath) {
        return Paths.get(fPath).toString();
    }

    public static String getAbsolutePath(String localPath) {
        return Paths.get(localPath).toAbsolutePath().toString();
    }

    public static boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }


    public static List<String> findFilesInFolder(String folder, String fileExtension){
        try (Stream<Path> walk = Files.walk(Paths.get(folder))) {
            return walk
                    .filter(p -> !Files.isDirectory(p))                                 // Not a directory
                    .map(Path::toString)                                                // Convert path to string
                    .filter(f -> f.endsWith(fileExtension))                             // Check end with
                    .collect(Collectors.toList());

        }catch (IOException e) {
            LoggerFactory.getLogger(FileHandler.class).error("Data source in folder " + folder + " with extension " + fileExtension + " not found");
            e.printStackTrace();
            return null;
        }
    }

    public static void createDirectories(List<String> dirPaths) {
        dirPaths.forEach(FileHandler::createDirectory);
    }

    public static void createDirectory(String dirPath) {
        try {
            Path dirFormattedPath = Paths.get(dirPath);
            if(!Files.exists(dirFormattedPath))
                Files.createDirectory(dirFormattedPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
