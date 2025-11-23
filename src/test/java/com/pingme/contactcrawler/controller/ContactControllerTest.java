package com.pingme.contactcrawler.controller;

import com.pingme.contactcrawler.entity.ContactInfo;
import com.pingme.contactcrawler.repository.ContactInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactControllerTest {

    @Mock
    private ContactInfoRepository contactInfoRepository;

    @InjectMocks
    private ContactController contactController;

    @Test
    void search_shouldReturnContactsByQuery_whenMatchFound() {
        // positive: есть совпадение по названию/домену
        String query = "netology";
        ContactInfo contact = new ContactInfo(
                "Netology",
                "https://netology.ru/contacts",
                "8 800 301-39-69",
                "support@netology.ru",
                "Москва"
        );

        when(contactInfoRepository
                .findByNameContainingIgnoreCaseOrWebsiteContainingIgnoreCase(query, query))
                .thenReturn(List.of(contact));

        List<ContactInfo> result = contactController.search(query);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Netology");
        assertThat(result.get(0).getWebsite()).contains("netology.ru");
    }

    @Test
    void search_shouldReturnEmptyList_whenNothingFound() {
        // negative: ничего не нашлось
        String query = "abracadabra";

        when(contactInfoRepository
                .findByNameContainingIgnoreCaseOrWebsiteContainingIgnoreCase(query, query))
                .thenReturn(List.of());

        List<ContactInfo> result = contactController.search(query);

        assertThat(result).isEmpty();
    }

    @Test
    void answer_shouldSortByNameAscAndPaginate() {
        // positive: сортировка по name asc + базовая пагинация
        ContactInfo c1 = new ContactInfo("МВидео", "https://mvideo.ru", "1", "a@mvideo.ru", null);
        ContactInfo c2 = new ContactInfo("Билайн", "https://beeline.ru", "2", "b@beeline.ru", null);
        ContactInfo c3 = new ContactInfo("Авито", "https://avito.ru", "3", "c@avito.ru", null);

        when(contactInfoRepository.findAll()).thenReturn(List.of(c1, c2, c3));

        List<ContactInfo> page = contactController.answer(
                "name",   // sortBy
                "asc",    // direction
                0,        // page
                2         // size
        );

        assertThat(page).hasSize(2);
        assertThat(page.get(0).getName()).isEqualTo("Авито");
        assertThat(page.get(1).getName()).isEqualTo("Билайн");
    }

    @Test
    void answer_shouldFallbackToIdSort_whenUnknownSortField() {
        // negative: sortBy некорректный (должен использоваться sort по id)
        ContactInfo c1 = new ContactInfo("A", "https://a.ru", "1", null, null);
        ContactInfo c2 = new ContactInfo("B", "https://b.ru", "2", null, null);
        ContactInfo c3 = new ContactInfo("C", "https://c.ru", "3", null, null);

        // имитируем, что сущности уже сохранены и получили id
        c1.setId(10L);
        c2.setId(5L);
        c3.setId(7L);

        when(contactInfoRepository.findAll()).thenReturn(List.of(c1, c2, c3));

        List<ContactInfo> result = contactController.answer(
                "unknownField", // sortBy невалидный
                "desc",         // сортируем по убыванию
                0,
                10
        );

        // ожидаем сортировку по id: 10, 7, 5
        assertThat(result).extracting(ContactInfo::getId)
                .containsExactly(10L, 7L, 5L);
    }

    @Test
    void answer_shouldReturnEmptyList_whenPageOutOfRange() {
        // negative: запрошена страница за пределами списка
        ContactInfo c1 = new ContactInfo("A", "https://a.ru", "1", null, null);
        ContactInfo c2 = new ContactInfo("B", "https://b.ru", "2", null, null);
        ContactInfo c3 = new ContactInfo("C", "https://c.ru", "3", null, null);

        when(contactInfoRepository.findAll()).thenReturn(List.of(c1, c2, c3));

        List<ContactInfo> result = contactController.answer(
                "name",
                "asc",
                2,   // page
                2    // size
        );

        assertThat(result).isEmpty();
    }
}
