package com.pingme.contactcrawler.controller;

import com.pingme.contactcrawler.entity.ContactInfo;
import com.pingme.contactcrawler.repository.ContactInfoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/contacts")
@Tag(
        name = "Работа с контактами"
)
public class ContactController {

    private final ContactInfoRepository contactInfoRepository;

    public ContactController(ContactInfoRepository contactInfoRepository) {
        this.contactInfoRepository = contactInfoRepository;
    }

    @Operation(
            summary = "Поиск контактов по названию/домену компании",
            description = """
                    Возвращает контакты из БД H2, где название организации или сайт содержат строку поиска.
                    
                    Примеры запросов:
                    - query = "Билайн"
                    - query = "mvideo"
                    - query = "netology"
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "Список найденных контактов",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = ContactInfo.class))
            )
    )
    @GetMapping("/search")
    public List<ContactInfo> search(
            @Parameter(
                    example = "netology",
                    required = true
            )
            @RequestParam("query") String query
    ) {
        return contactInfoRepository
                .findByNameContainingIgnoreCaseOrWebsiteContainingIgnoreCase(query, query);
    }

    @Operation(
            summary = "Получить все найденные контакты (ответ краулера)",
            description = """
                    Возвращает все контакты, найденные краулером, с возможностью сортировки и пагинации.
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "Список контактов с учётом сортировки и пагинации",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = ContactInfo.class))
            )
    )
    @GetMapping("/answer")
    public List<ContactInfo> answer(
            @Parameter(
                    description = "Поле сортировки: id, name, website, phone, email (По умолчанию id).",
                    example = "name"
            )
            @RequestParam(defaultValue = "id") String sortBy,

            @Parameter(
                    description = "Направление сортировки: asc (по возрастанию) или desc (по убыванию).",
                    example = "asc"
            )
            @RequestParam(defaultValue = "asc") String direction,

            @Parameter(
                    description = "Номер страницы, начиная с 0 (По умолчанию 0).",
                    example = "0"
            )
            @RequestParam(defaultValue = "0") int page,

            @Parameter(
                    description = "Размер страницы - сколько записей вернуть (По умолчанию 10).",
                    example = "10"
            )
            @RequestParam(defaultValue = "10") int size
    ) {
        // достаем все записи из H2
        List<ContactInfo> all = contactInfoRepository.findAll();

        // выбираем компаратор в зависимости от sortBy
        Comparator<ContactInfo> comparator;

        switch (sortBy) {
            case "name":
                comparator = Comparator.comparing(
                        ContactInfo::getName,
                        Comparator.nullsLast(String::compareToIgnoreCase)
                );
                break;
            case "website":
                comparator = Comparator.comparing(
                        ContactInfo::getWebsite,
                        Comparator.nullsLast(String::compareToIgnoreCase)
                );
                break;
            case "phone":
                comparator = Comparator.comparing(
                        ContactInfo::getPhone,
                        Comparator.nullsLast(String::compareToIgnoreCase)
                );
                break;
            case "email":
                comparator = Comparator.comparing(
                        ContactInfo::getEmail,
                        Comparator.nullsLast(String::compareToIgnoreCase)
                );
                break;
            default:
                // по умолчанию сортируем по id
                comparator = Comparator.comparing(ContactInfo::getId);
        }

        if ("desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }

        return all.parallelStream()
                .sorted(comparator)
                .skip((long) page * size) // пагинация
                .limit(size)
                .collect(Collectors.toList());
    }
}
