package com.wallaceespindola.reportcomposer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReportComposerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportComposerApplication.class, args);
    }
}
