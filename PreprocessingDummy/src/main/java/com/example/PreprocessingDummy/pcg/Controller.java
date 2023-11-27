package com.example.PreprocessingDummy.pcg;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/preprocessing-dummy")
public class Controller {

    // TODO: for testing
    @PostMapping(value = "/send-notification")
    private void startPipeline(@RequestParam("notification_file") String notification_file,
                               @RequestParam("file_extension") String file_extension) {

        PreprocessingMakeCallToKG sender = new PreprocessingMakeCallToKG(notification_file, file_extension);
        sender.startWiremock();
        sender.sendMetadata();
        System.out.println("Continue preprocessing");
    }


}
