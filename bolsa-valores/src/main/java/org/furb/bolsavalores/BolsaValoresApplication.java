package org.furb.bolsavalores;

import org.furb.bolsavalores.config.AppConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppConfig.class)
public class BolsaValoresApplication {
    public static void main(String[] args) {
        SpringApplication.run(BolsaValoresApplication.class, args);
    }
}
