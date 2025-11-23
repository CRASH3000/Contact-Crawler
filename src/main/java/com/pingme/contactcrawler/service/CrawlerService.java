package com.pingme.contactcrawler.service;

import com.pingme.contactcrawler.entity.ContactInfo;
import com.pingme.contactcrawler.repository.ContactInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.scheduling.annotation.Scheduled;
import com.pingme.contactcrawler.logging.LoggingService;
import com.pingme.contactcrawler.config.DefaultSitesProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CrawlerService {
    private final LoggingService loggingService;
    private final ContactInfoRepository contactInfoRepository;
    private final WebClient webClient;

    // Кастомный пул потоков для краулера
    private final ExecutorService executorService =
            Executors.newFixedThreadPool(4);

    // Потокобезопасные структуры
    private final Queue<String> urlQueue = new ConcurrentLinkedQueue<>();
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private final DefaultSitesProvider defaultSitesProvider;

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

        // Запускаем несколько воркеров в пуле потоков
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
        String url;

        while ((url = urlQueue.poll()) != null) {
            // Ограничиваем количество страниц
            if (visitedUrls.size() >= MAX_PAGES) {
                return;
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
                    String msg = "WARN: " + url + " — не удалось получить HTML (пустой ответ)";
                    loggingService.log(msg);
                    statusMessages.add(msg);
                    continue;
                }

                String email = extractEmail(html);
                List<String> phones = extractPhones(html);

                if (email != null || !phones.isEmpty()) {

                    String phonesStr = String.join(", ", phones);

                    ContactInfo info = new ContactInfo(
                            url,
                            url,
                            phonesStr,
                            email,
                            null
                    );
                    contactInfoRepository.save(info);

                    String msg = "OK: " + url + " — телефонов: "
                            + phones.size() + ", email: " + (email != null ? email : "нет");
                    loggingService.log(msg);
                    statusMessages.add(msg);

                } else {
                    // КЛЮЧЕВОЙ МОМЕНТ: если контактов нет в чистом HTML
                    String msg = "WARN: " + url
                            + " — телефоны и email не найдены в HTML. "
                            + "Возможно, контакты подгружаются через JavaScript "
                            + "или страница использует нестандартную верстку.";
                    loggingService.log(msg);
                    statusMessages.add(msg);
                }

                for (String link : extractLinks(html)) {
                    if (!visitedUrls.contains(link)) {
                        urlQueue.offer(link);
                    }
                }

            } catch (Exception e) {
                String msg = "ERROR: " + url
                        + " — ошибка при загрузке: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                loggingService.log(msg);
                statusMessages.add(msg);
            }
        }
    }

    private String extractEmail(String html) {
        // выкидываем теги, оставляем только текст
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
            String raw = matcher.group().trim();              // как номер записан в тексте
            String digits = raw.replaceAll("\\D+", "");       // только цифры

            // отсеиваем совсем короткий/длинный номер
            if (digits.length() < 10 || digits.length() > 15) {
                continue;
            }

            // ключ последние 10 цифр (чтобы +7 и 8 считались одним номером)
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
        List<String> results = crawl(urls); // краул запускаем один раз

        loggingService.log("INFO: плановый запуск краулера завершён. Обработано URL: "
                + urls.size());

        for (String msg : results) {
            loggingService.log("SCHEDULED: " + msg);
        }
    }
}
