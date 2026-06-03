package com.meada.whatsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do backend Meada WhatsApp.
 *
 * @SpringBootApplication habilita component-scan a partir deste pacote
 * (com.meada.whatsapp) e a auto-configuração do Spring Boot — incluindo o
 * DataSource/Hikari a partir das propriedades spring.datasource.* do
 * application.yml. Nenhum bean é declarado manualmente aqui; configurações
 * específicas entram em classes próprias quando necessário.
 */
@SpringBootApplication
public class WhatsappApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatsappApplication.class, args);
    }
}
