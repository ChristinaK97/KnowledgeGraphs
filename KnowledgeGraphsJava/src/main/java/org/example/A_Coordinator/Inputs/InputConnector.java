package org.example.A_Coordinator.Inputs;

import org.example.A_Coordinator.Pipeline;
import org.example.A_Coordinator.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.example.A_Coordinator.config.Config.*;
import static org.example.util.FileHandler.getFileExtension;
import static org.example.util.FileHandler.getPath;

@RestController
@RequestMapping("/KGInputPoint")
public class InputConnector {

    public static boolean DOCKER_ENV = true;

    public static String FINTECH = "fintech";
    public static String HEALTH = "health";
    public static String CTI = "CTI";

    public static String USE_CASE;              //= HEALTH;
    public static String FILENAME;              //= "gfh.dcm";
    public static String resourcePath = getPath("resources");


    @Autowired
    public InputConnector() {}

    @PostMapping(value = "/testPipeline")
    private void startPipeline(@RequestParam("UseCase") String UseCase,
                               @RequestParam("filename") String filename) {
        USE_CASE = UseCase;
        FILENAME = filename;
        // LoggerFactory.getLogger(InputConnector.class).info(UseCase + " " + filename);
        Pipeline pipeline = new Pipeline(setupConfig(USE_CASE, FILENAME));
        pipeline.run();
    }

    private Config setupConfig(String UseCase, String filename) {
        String FileExtension = filename.equals("SQL") ? "SQL" :
                getFileExtension(filename);
        return new Config(UseCase, FileExtension);
    }


}
