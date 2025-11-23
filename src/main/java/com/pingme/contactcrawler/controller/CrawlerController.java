package com.pingme.contactcrawler.controller;

import com.pingme.contactcrawler.service.CrawlerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/crawler")
@Tag(
        name = "Запуск краулера"
)
public class CrawlerController {

    private final CrawlerService crawlerService;

    public CrawlerController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @Operation(
            summary = "Запустить краулер вручную",
            description = """
                    Принимает список стартовых URL (обычно страницы с контактами компаний),
                    запускает многопоточный краулер и сохраняет найденные телефоны/почты в БД H2.
                    
                    Пример тела запроса (Можно отправлять как один URL так и несколько):
                    ```
                    {
                      "startUrls": [
                        "https://www.sberbank.ru/ru/person/call_center",
                        "https://moskva.beeline.ru/customers/contact-page",
                        "https://netology.ru/contacts"
                      ]
                    }
                    ```
                    """
    )

    @ApiResponse(
            responseCode = "200",
            description = "Результаты обработки каждого URL (OK/WARN/ERROR)",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = String.class))
            )
    )
    @PostMapping("/start")
    public List<String> startCrawling(
            @RequestBody(
                    description = "Список URL-адресов, с которых краулер начнет обход",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = String.class))
                    )
            )
            @org.springframework.web.bind.annotation.RequestBody StartRequest request
    ) {
        List<String> startUrls = request.startUrls();
        return crawlerService.crawl(startUrls);
    }


    public record StartRequest(List<String> startUrls) {}
}
