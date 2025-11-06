package org.furb.bolsavalores;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BolsaValoresApplication {
    public static void main(String[] args) {
        SpringApplication.run(BolsaValoresApplication.class, args);
    }

}
