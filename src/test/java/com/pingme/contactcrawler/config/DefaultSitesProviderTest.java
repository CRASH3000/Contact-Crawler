package com.pingme.contactcrawler.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSitesProviderTest {

    @Test
    void getDefaultSites_shouldReturnNonEmptyList() {
        // positive: список не пустой
        DefaultSitesProvider provider = new DefaultSitesProvider();

        List<String> sites = provider.getDefaultSites();

        assertThat(sites).isNotEmpty();
    }

    @Test
    void getDefaultSites_shouldNotContainNullOrBlankUrls() {
        // negative-проверка: нет null/пустых строк
        DefaultSitesProvider provider = new DefaultSitesProvider();

        List<String> sites = provider.getDefaultSites();

        assertThat(sites)
                .doesNotContainNull()
                .allMatch(url -> !url.isBlank(), "URL не должен быть пустым");
    }
}
