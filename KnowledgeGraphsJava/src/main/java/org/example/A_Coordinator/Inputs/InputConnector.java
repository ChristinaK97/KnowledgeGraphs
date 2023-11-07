package org.example.A_Coordinator.Inputs;

import org.example.A_Coordinator.Pipeline;
import org.example.A_Coordinator.config.Config;

import static org.example.A_Coordinator.config.Config.*;
import static org.example.util.FileHandler.getPath;

public class InputConnector {

    public static String USE_CASE = FINTECH;
    public static String filename = "gfh.csv";
    public static String resourcePath = getPath("resources");


    public InputConnector() {
        Pipeline pipeline = new Pipeline(setupConfig(USE_CASE, filename));
        pipeline.run();
    }

    private Config setupConfig(String UseCase, String filename) {
        String FileExtension = filename.equals("SQL") ? "SQL" :
                filename.substring(filename.lastIndexOf(".")+1);
        return new Config(UseCase, FileExtension);
    }

    public static void main(String[] args) {

        new InputConnector();
    }
}
