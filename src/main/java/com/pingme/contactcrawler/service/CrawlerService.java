package com.pingme.contactcrawler.service;

import com.pingme.contactcrawler.config.DefaultSitesProvider;
import com.pingme.contactcrawler.entity.ContactInfo;
import com.pingme.contactcrawler.logging.LoggingService;
import com.pingme.contactcrawler.repository.ContactInfoRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.micrometer.core.instrument.Metrics.globalRegistry;

@Service
public class CrawlerService {
    private final LoggingService loggingService;
    private final ContactInfoRepository contactInfoRepository;
    private final WebClient webClient;
    private final DefaultSitesProvider defaultSitesProvider;

    private final Timer parseTimer = Timer.builder("crawler.parse")
            .description("Time spent on parsing HTML (extract email/phones/links)")
            .publishPercentileHistogram()
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(globalRegistry);

    private final Counter parseSuccess = Counter.builder("crawler.parse.success")
            .description("Number of successful page parses")
            .register(globalRegistry);

    private final Counter parseError = Counter.builder("crawler.parse.error")
            .description("Number of failed page parses")
            .register(globalRegistry);

    private final Counter errHttp4xx = Counter.builder("crawler.parse.error.reason")
            .tag("reason", "http_4xx")
            .description("Parse errors grouped by reason")
            .register(globalRegistry);

    private final Counter errHttp5xx = Counter.builder("crawler.parse.error.reason")
            .tag("reason", "http_5xx")
            .description("Parse errors grouped by reason")
            .register(globalRegistry);

    private final Counter errTimeout = Counter.builder("crawler.parse.error.reason")
            .tag("reason", "timeout")
            .description("Parse errors grouped by reason")
            .register(globalRegistry);

    private final Counter errConnection = Counter.builder("crawler.parse.error.reason")
            .tag("reason", "connection")
            .description("Parse errors grouped by reason")
            .register(globalRegistry);

    private final Counter errEmptyHtml = Counter.builder("crawler.parse.error.reason")
            .tag("reason", "empty_html")
            .description("Parse errors grouped by reason")
            .register(globalRegistry);

    private final Counter errOther = Counter.builder("crawler.parse.error.reason")
            .tag("reason", "other")
            .description("Parse errors grouped by reason")
            .register(globalRegistry);

    private final Counter dbSaved = Counter.builder("crawler.db.saved")
            .description("Number of ContactInfo records saved to DB")
            .register(globalRegistry);

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Queue<String> urlQueue = new ConcurrentLinkedQueue<>();
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?:\\+7|8)?\\s*\\(?\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,6}");

    private static final int MAX_PAGES = 20;

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "href=[\"'](https?://[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    public CrawlerService(ContactInfoRepository contactInfoRepository,
                          LoggingService loggingService,
                          DefaultSitesProvider defaultSitesProvider) {
        this.contactInfoRepository = contactInfoRepository;
        this.loggingService = loggingService;
        this.defaultSitesProvider = defaultSitesProvider;
        this.webClient = WebClient.create();
    }

    // Запуск краулинга со списком стартовых URL
    public List<String> crawl(List<String> startUrls) {
        if (startUrls == null || startUrls.isEmpty()) {
            return List.of("WARN: список стартовых URL пуст, краулер не запущен");
        }

        urlQueue.clear();
        visitedUrls.clear();
        urlQueue.addAll(startUrls);

        List<String> statusMessages = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            futures.add(executorService.submit(() -> workerLoop(statusMessages)));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                loggingService.log("ERROR: ошибка в одном из потоков краулера: " + e.getMessage());
            }
        }

        return statusMessages;
    }

    private void workerLoop(List<String> statusMessages) {
        // ✅ FIX 4.1 (N+1): буфер + пакетные вставки в БД
        final int BATCH_SIZE = 50;
        final List<ContactInfo> buffer = new ArrayList<>(BATCH_SIZE);

        String url;

        try {
            while ((url = urlQueue.poll()) != null) {

                if (visitedUrls.size() >= MAX_PAGES) {
                    break;
                }

                if (!visitedUrls.add(url)) {
                    continue;
                }

                try {
                    String html = webClient
                            .get()
                            .uri(url)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofSeconds(10));

                    if (html == null) {
                        parseError.increment();
                        errEmptyHtml.increment();

                        String msg = "WARN: " + url + " — не удалось получить HTML (пустой ответ)";
                        loggingService.log(msg);
                        statusMessages.add(msg);
                        continue;
                    }

                    Timer.Sample sample = Timer.start();
                    String email = extractEmail(html);
                    List<String> phones = extractPhones(html);
                    List<String> links = extractLinks(html);
                    sample.stop(parseTimer);

                    parseSuccess.increment();

                    if (email != null || !phones.isEmpty()) {
                        String phonesStr = String.join(", ", phones);

                        ContactInfo info = new ContactInfo(
                                url,
                                url,
                                phonesStr,
                                email,
                                null
                        );

                        buffer.add(info);
                        dbSaved.increment();

                        if (buffer.size() >= BATCH_SIZE) {
                            contactInfoRepository.saveAll(buffer);
                            buffer.clear();
                        }

                        String msg = "OK: " + url + " — телефонов: "
                                + phones.size() + ", email: " + (email != null ? email : "нет");
                        loggingService.log(msg);
                        statusMessages.add(msg);

                    } else {
                        String msg = "WARN: " + url
                                + " — телефоны и email не найдены в HTML. "
                                + "Возможно, контакты подгружаются через JavaScript "
                                + "или страница использует нестандартную верстку.";
                        loggingService.log(msg);
                        statusMessages.add(msg);
                    }

                    for (String link : links) {
                        if (!visitedUrls.contains(link)) {
                            urlQueue.offer(link);
                        }
                    }

                } catch (Exception e) {
                    parseError.increment();
                    incrementErrorReason(e);

                    String msg = "ERROR: " + url
                            + " — ошибка при загрузке: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                    loggingService.log(msg);
                    statusMessages.add(msg);
                }
            }
        } finally {
            if (!buffer.isEmpty()) {
                contactInfoRepository.saveAll(buffer);
                buffer.clear();
            }
        }
    }

    private void incrementErrorReason(Throwable e) {
        String reason = classifyErrorReason(e);
        switch (reason) {
            case "http_4xx" -> errHttp4xx.increment();
            case "http_5xx" -> errHttp5xx.increment();
            case "timeout" -> errTimeout.increment();
            case "connection" -> errConnection.increment();
            case "empty_html" -> errEmptyHtml.increment();
            default -> errOther.increment();
        }
    }

    private String classifyErrorReason(Throwable e) {
        if (e instanceof WebClientResponseException wcre) {
            HttpStatusCode status = wcre.getStatusCode();
            if (status.is4xxClientError()) return "http_4xx";
            if (status.is5xxServerError()) return "http_5xx";
            return "other";
        }

        if (e instanceof WebClientRequestException) {
            if (hasCause(e, ConnectException.class) || hasCause(e, UnknownHostException.class)) {
                return "connection";
            }
        }

        if (hasCause(e, TimeoutException.class) || hasCause(e, java.util.concurrent.TimeoutException.class)) {
            return "timeout";
        }
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout")) {
            return "timeout";
        }

        return "other";
    }

    private boolean hasCause(Throwable e, Class<? extends Throwable> causeClass) {
        Throwable cur = e;
        while (cur != null) {
            if (causeClass.isInstance(cur)) return true;
            cur = cur.getCause();
        }
        return false;
    }

    private String extractEmail(String html) {
        String text = html.replaceAll("<[^>]*>", " ");
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private List<String> extractPhones(String html) {
        String text = html
                .replace("&nbsp;", " ")
                .replace("&#160;", " ");

        text = text.replaceAll("<[^>]*>", " ");

        java.util.Map<String, String> phonesMap = new java.util.LinkedHashMap<>();

        java.util.regex.Matcher matcher = PHONE_PATTERN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group().trim();
            String digits = raw.replaceAll("\\D+", "");

            if (digits.length() < 10 || digits.length() > 15) {
                continue;
            }

            String key = digits.substring(digits.length() - 10);
            phonesMap.putIfAbsent(key, raw);
        }

        return new java.util.ArrayList<>(phonesMap.values());
    }

    private List<String> extractLinks(String html) {
        List<String> links = new ArrayList<>();
        Matcher matcher = LINK_PATTERN.matcher(html);
        while (matcher.find()) {
            String link = matcher.group(1);
            links.add(link);
        }
        return links;
    }

    // Автоматический запуск краулера по расписанию
    @Scheduled(initialDelay = 15000, fixedDelay = 300000)
    public void scheduledCrawl() {
        List<String> urls = defaultSitesProvider.getDefaultSites();
        List<String> results = crawl(urls);

        loggingService.log("INFO: плановый запуск краулера завершён. Обработано URL: " + urls.size());

        for (String msg : results) {
            loggingService.log("SCHEDULED: " + msg);
        }
    }
}
