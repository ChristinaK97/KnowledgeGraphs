package org.example.A_Coordinator.Inputs;

import org.example.A_Coordinator.Pipeline;
import org.example.A_Coordinator.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Paths;

import static org.example.util.FileHandler.*;

@RestController
@RequestMapping("/KGInputPoint")
public class InputConnector {
    public static String  WORKDIR = Paths.get(System.getProperty("user.dir")).toString();
    public static boolean IS_DOCKER_ENV = WORKDIR.startsWith("/KnowledgeGraphsApp");
    public static String  resourcePath = getPath("resources");

    public static String FINTECH = "fintech";
    public static String HEALTH = "health";
    public static String CTI = "CTI";


    @Autowired
    public InputConnector() {}

    @PostMapping(value = "/testPipeline")
    private void startPipeline(@RequestParam("UseCase") String UseCase,
                               @RequestParam("filename") String filename) {
        // LoggerFactory.getLogger(InputConnector.class).info(UseCase + " " + filename);
        Pipeline pipeline = new Pipeline(setupConfig(UseCase, filename));
        pipeline.run();
    }

    private Config setupConfig(String UseCase, String filename) {
        String FileExtension = filename.equals("SQL") ? "SQL" :
                getFileExtension(filename);
        return new Config(UseCase, FileExtension);
    }


}
