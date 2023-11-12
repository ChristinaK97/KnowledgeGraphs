package org.example.D_MappingGeneration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;
import java.time.Duration;

import static org.example.A_Coordinator.Inputs.InputConnector.DOCKER_ENV;
import static org.example.A_Coordinator.Pipeline.config;
import static org.example.util.FileHandler.getAbsolutePath;

public class BertMap {

    private static String BertMapEndpoint =
            String.format("http://%s:7532/start_bertmap", DOCKER_ENV ? "bertmap" : "localhost");

    private class BertmapRequest {
        private String use_case;
        private String base_output_path;
        private boolean run_for_do_mapping;
        @JsonProperty("POntology_path")
        private String POntology_path;
        @JsonProperty("DOntology_path")
        private String DOntology_path;
        @JsonProperty("DPV_path")
        private String DPV_path;

        public BertmapRequest(String use_case, String base_output_path, boolean run_for_do_mapping,
                              String POntology_path, String DOntology_path, String DPV_path)
        {
            this.use_case = use_case;
            this.base_output_path = base_output_path;
            this.run_for_do_mapping = run_for_do_mapping;
            this.POntology_path = getAbsolutePath(POntology_path);
            this.DOntology_path = DOntology_path;
            this.DPV_path = DPV_path;
        }

        public String getUse_case() {
            return use_case;
        }
        public void setUse_case(String use_case) {
            this.use_case = use_case;
        }
        public String getBase_output_path() {
            return base_output_path;
        }
        public void setBase_output_path(String base_output_path) {
            this.base_output_path = base_output_path;
        }
        public boolean isRun_for_do_mapping() {
            return run_for_do_mapping;
        }
        public void setRun_for_do_mapping(boolean run_for_do_mapping) {
            this.run_for_do_mapping = run_for_do_mapping;
        }
        public String getPOntology_path() {
            return POntology_path;
        }
        public void setPOntology_path(String POntology_path) {
            this.POntology_path = POntology_path;
        }
        public String getDOntology_path() {
            return DOntology_path;
        }
        public void setDOntology_path(String DOntology_path) {
            this.DOntology_path = DOntology_path;
        }
        public String getDPV_path() {
            return DPV_path;
        }
        public void setDPV_path(String DPV_path) {
            this.DPV_path = DPV_path;
        }
    }

    public JsonObject startBertmap(boolean run_for_do_mapping) {

        WebClient webClient = WebClient.create();

        BertmapRequest requestBody = new BertmapRequest(
            config.In.UseCase,
            config.DOMap.base_output_path,
            run_for_do_mapping,
            config.Out.POntology,
            config.DOMap.TgtOntology,
            config.PiiMap.TgtOntology
        );

        String response = webClient.post()
                .uri(BertMapEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofHours(64))
                .block();

        // Parse JSON string to JsonObject
        if (response != null)
            return JsonParser.parseString(response).getAsJsonObject();
        else {
            LoggerFactory.getLogger(BertMap.class).error("bertmap service returned null!");
            return null;
        }
    }
}
