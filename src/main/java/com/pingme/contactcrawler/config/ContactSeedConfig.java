package com.pingme.contactcrawler.config;

import com.pingme.contactcrawler.entity.ContactInfo;
import com.pingme.contactcrawler.repository.ContactInfoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContactSeedConfig {

    @Bean
    public CommandLineRunner seedData(ContactInfoRepository repository) {
        return args -> {
            // Тестовые данные
            if (repository.count() == 0) {
                repository.save(new ContactInfo(
                        "Домашний интернет Билайн",
                        "https://beeline.ru",
                        "+7 800 700 8000",
                        "support@beeline.ru",
                        "Россия, Москва"
                ));

                repository.save(new ContactInfo(
                        "МВидео",
                        "https://www.mvideo.ru",
                        "+7 800 600 7775",
                        "info@mvideo.ru",
                        "Россия, Москва"
                ));

                repository.save(new ContactInfo(
                        "Барбершоп Москва",
                        "https://barbershop.ru",
                        "+7 495 123 4567",
                        null, // без почты
                        "Москва"
                ));

                repository.save(new ContactInfo(
                        "Единый номер поддержки",
                        "https://example.ru",
                        "101, 102, 103",
                        null,
                        "Россия"
                ));

                repository.save(new ContactInfo(
                        "Служба безопасности банка",
                        "https://bank.ru",
                        "900",
                        null,
                        "Россия"
                ));
            }
        };
    }
}
