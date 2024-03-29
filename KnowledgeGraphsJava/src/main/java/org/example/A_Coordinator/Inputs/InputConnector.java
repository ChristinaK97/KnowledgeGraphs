package org.example.A_Coordinator.Inputs;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.example.A_Coordinator.Kafka.KafkaProducerService;
import org.example.A_Coordinator.Pipeline;
import org.example.A_Coordinator.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.example.util.FileHandler.*;

@RestController
@RequestMapping("/KGInputPoint")
public class InputConnector {

    public static String FINTECH = "fintech";
    public static String HEALTH = "health";
    public static String CTI = "threat-intelligence";

    private static final Logger LOGGER = LoggerFactory.getLogger(InputConnector.class);

    private final PreprocessingFilesDownloader downloader;
    private Config config;

    private final KafkaProducerService kafkaProducerService;


    @Autowired
    public InputConnector(PreprocessingFilesDownloader downloader, KafkaProducerService kafkaProducerService) {
        this.downloader = downloader;
        this.kafkaProducerService = kafkaProducerService;
    }


    private Config setupConfig(String UseCase, String filename, PreprocessingNotification notification) {
        String FileExtension = filename.equals("SQL") ? "SQL" :
                getFileExtension(filename);
        return new Config(UseCase, FileExtension, notification);
    }


    /** Receive post request / notification from preprocessing */
    @PostMapping(value = "/fileMetadata")
    public ResponseEntity<String> receivePreprocessingNotification(
            @RequestBody String notificationString) {
        try {
            PreprocessingNotification notification = new Gson().fromJson(notificationString, PreprocessingNotification.class);
            notification.setReceivedFromPreprocessing(true);
            LOGGER.info("Knowledge Graphs received:\n" + notification);
            String UseCase  = notification.getDomain();
            String fileName = notification.getFilename();
            this.config = setupConfig(UseCase, fileName, notification);

            if(config.In.isDSON())
                notification.turnPiiTagNamesToCodes();

            Path fileDownloadPath = Paths.get(String.format("%s/%s", config.In.DownloadedDataDir, fileName));

            if (downloadFile(fileName, fileDownloadPath))
                return sendResponse(true, 1, null);
            else
                return sendResponse(false, 2, null);
        } catch (Exception e) {
            return sendResponse(false, 3, e.getMessage());
        }
    }

    /** Return a response */
    private ResponseEntity<String> sendResponse(boolean okStatus, int type, String exceptionMessage) {
        String responseBody = switch (type) {
            case 1 -> "Metadata post request to KGs succeeded";
            case 2 -> "Metadata post request to KGs succeeded, but file download failed.";
            case 3 -> "Metadata post request to KGs failed: " + exceptionMessage;
            default -> null;
        };
        LOGGER.info(responseBody);
        if(okStatus)
            return ResponseEntity.ok(responseBody);
        else
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(responseBody);
    }


    private boolean downloadFile(String fileName, Path fileDownloadPath) {
        /* 1. Call service to download the file and handle response
         * 2. Use fileMono to access the downloaded file (i.e., save file a local directory or process it further) */
        try {
            Mono<Resource> fileMono = downloader.downloadFile(fileName, this.config.In.downloadOriginal);   // 1
            if (fileMono != null) {
                processFileMono(fileMono, fileDownloadPath);    // 2
                return true;
            } else {
                LOGGER.error(fileName + " download failed: fileMono == null");
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Exception during download " + fileName);
            return false;
        }
    }

    private void processFileMono(Mono<Resource> fileMono, Path fileDownloadPath) {
        /* 1. Process the downloaded file (resource) here, eg Copy the content to a local file
         * 2. Now you can work with the local file as needed
         * 3. Handle any exceptions that may occur while processing the file
         * 4. Handle errors, such as network issues, here
         * 5. This block will be executed when the download is complete
         *    perform long processing here so the preprocessing can terminate
         */
        fileMono.subscribe(resource -> {
            try {
                // 1
                InputStream inputStream = resource.getInputStream();
                Files.copy(inputStream, fileDownloadPath, StandardCopyOption.REPLACE_EXISTING);
                // 2
            } catch (IOException e) { // 3
                LOGGER.error("Error while saving to local file for" + fileDownloadPath);
            }
        }, error -> { // 4
            LOGGER.error("Error while processing fileMono for " + fileDownloadPath);
            error.printStackTrace();
        }, () -> {
            // 5
            boolean isDownloadSuccessful = Files.exists(fileDownloadPath);
            if(isDownloadSuccessful) {
                LOGGER.info("File " + fileDownloadPath + " saved successfully");
                runPipeline();
            }else
                LOGGER.error("File was not downloaded successfully: Downloaded file not found at "
                        + fileDownloadPath + ". Skipping KGs component!");
        });
    }


    private void runPipeline() {
        LOGGER.info("RUN KGs PIPELINE...");
        new Pipeline(this.config, kafkaProducerService).run();
        LOGGER.info("KGs PIPELINE FINISHED!");
    }

// =====================================================================================================================
    // TODO: for testing
    @PostMapping(value = "/testPipeline")
    private void startPipeline(@RequestParam("UseCase") String UseCase,
                               @RequestParam("filename") String filename) {
        LOGGER.info("Run pipeline for " + UseCase + " " + filename);
        PreprocessingNotification notification = new PreprocessingNotification();
        notification.setReceivedFromPreprocessing(false);
        Pipeline pipeline = new Pipeline(
                setupConfig(UseCase, filename, notification),
                kafkaProducerService
        );
        pipeline.run();
        LOGGER.info("KGs pipeline finished!");
    }
// =====================================================================================================================

}
