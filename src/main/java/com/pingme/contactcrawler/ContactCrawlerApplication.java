package com.pingme.contactcrawler;

import com.pingme.contactcrawler.thread.SimpleStatusLogger;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ContactCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContactCrawlerApplication.class, args);
    }

    @Bean
    public CommandLineRunner startSimpleStatusLogger() {
        return args -> {
            SimpleStatusLogger logger = new SimpleStatusLogger(15_000L); // лог раз в 15 секунд

            Thread loggerThread = new Thread(logger, "simple-status-logger-thread");
            loggerThread.setDaemon(true);
            loggerThread.start();
        };
    }
}
