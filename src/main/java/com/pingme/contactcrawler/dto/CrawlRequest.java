package com.pingme.contactcrawler.dto;

import java.util.List;

// Простая модель запроса для запуска краулера
public class CrawlRequest {

    private List<String> startUrls;

    public CrawlRequest() {
    }

    public CrawlRequest(List<String> startUrls) {
        this.startUrls = startUrls;
    }

    public List<String> getStartUrls() {
        return startUrls;
    }

    public void setStartUrls(List<String> startUrls) {
        this.startUrls = startUrls;
    }
}
