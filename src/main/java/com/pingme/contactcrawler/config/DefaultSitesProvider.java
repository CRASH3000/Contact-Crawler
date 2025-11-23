package com.pingme.contactcrawler.config;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultSitesProvider {

    private final List<String> defaultSites = List.of(
            "https://www.mosgortrans.ru/contact",
            "https://support.mts.ru/contacts"
    );

    public List<String> getDefaultSites() {
        return defaultSites;
    }
}

