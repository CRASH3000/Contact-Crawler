

## О проекте

---
<img src="docs/The.png" alt="Скрин Swagger" width="350">

Contact Crawler - это учебный проект, представляющего из себя 
многопоточное веб-приложение на **Spring Boot**, который 
ищет данные об организации используя URL-адреса сайтов компаний.

**Функционал:**

* Многопоточный краулер получает список URL с контактами компаний.
* Через `WebClient` тянет HTML и с помощью регулярок извлекает:
    * номер телефоны (российский формат `+7` / `8` и 10 цифр);
    * email.
* Результат сохраняет в **H2 in-memory БД** в таблицу `contact_info`.
* Через REST-API можно:
    * запустить краулер вручную;
    * посмотреть все найденные контакты с сортировкой и пагинацией;
    * искать по названию/домену.

Так же имеется автозаполнение БД тремя тестовыми контактами 
при старте и плановый запуск краулера по расписанию.

⚠️ **Ограничения:**

* Краулер видит только **сырой HTML**.
  Если сайт рисует телефоны через JavaScript/AJAX, то сервис их не найдет.
* Телефоны хранятся **одной строкой**, несколько номеров склеиваются через запятую.
* Лимит обхода не больше **20 страниц за запуск**.
* БД H2 в памяти. После перезапуска данные найденные краулером пропадают.

## Как запустить проект?

---

### Что нужно:
>* **JDK 17+**
>* Свободный порт **8080**
>* Доступ в интернет

Все зависимости и Gradle уже в проекте (`gradlew`), отдельно ничего ставить не нужно.

### Запуск приложения

В корне проекта:

```bash
./gradlew bootRun        # macOS / Linux
gradlew.bat bootRun      # Windows
```

Приложение поднимется на:
`http://localhost:8080`

Для завершения приложения необходимо нажать Ctrl + C в том же окне терминала. 

### Проверка логики через Swagger

Swagger UI:
`http://localhost:8080/swagger-ui/index.html`

Дальше последовательность для проверки:

1. **Проверить, что есть стартовые данные**

    * Открыть блок **«Работа с контактами»**
    * Вызвать `GET /api/contacts/answer` → **Try it out → Execute**
    * В ответе должны быть тестовых контакта.


2. **Запустить краулер вручную**

    * Открыть блок **«Запуск краулера»**

    * `POST /api/crawler/start` → **Try it out**

    * В тело запроса вставить, например:

      ```json
      {
        "startUrls": [
          "https://netology.ru/contacts",
          "https://moskva.beeline.ru/customers/contact-page"
        ]
      }
      ```

    * Нажать **Execute**.
      В ответе придёт массив строк со статусами по каждому URL (`OK / WARN / ERROR`).


3. **Посмотреть новые контакты**

    * Снова вызвать `GET /api/contacts/answer`.
    * В конце списка должны появиться новые записи с телефонами/email, если на страницах они были в HTML.


4. **Проверить поиск**

    * `GET /api/contacts/search`
    * Пример: `query=netology` или `query=beeline` → **Execute**
    * В ответе остаются только подходящие контакты.

### Запуск юнит-тестов

```bash
./gradlew clean test        # macOS / Linux
gradlew.bat clean test      # Windows
```
---

## Проверка работы приложения и метрик: для управления производительностью приложения.

### 1) Запуск приложения (Если до этого приложение не было запущено)
Запустить `ContactCrawlerApplication` стандартно (из IDE) или из корня проекта:

```bash
./gradlew bootRun
````

> Если порт занят (8080), проверь процесс:
>
> ```bash
> lsof -nP -iTCP:8080 -sTCP:LISTEN
> ```
>
> Остановить процесс:
>
> ```bash
> kill <PID>
> ```
>
> Если не помогло:
>
> ```bash
> kill -9 <PID>
> ```

### 2) Проверка Health

Открыть в браузере:

* [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

Ожидаемый результат: статус `{"status":"UP"}` приложение поднялось и отвечает.

### 3) Проверка списка метрик Actuator

Открыть список метрик:

* [http://localhost:8080/actuator/metrics](http://localhost:8080/actuator/metrics)

### 4) Создать статистику чтобы оживить метрики

Можно через Swagger, либо просто несколько раз выполнить команды в корне проекта:

* [http://localhost:8080/api/contacts/answer?page=0&size=5](http://localhost:8080/api/contacts/answer?page=0&size=5)
* [http://localhost:8080/api/contacts/search?query=beeline](http://localhost:8080/api/contacts/search?query=beeline)

### 5) Посмотреть метрики HTTP и Prometheus-экспорт

* Метрика HTTP-запросов (простой вариант):

    * [http://localhost:8080/actuator/metrics/http.server.requests](http://localhost:8080/actuator/metrics/http.server.requests)
* Prometheus формат (расширенный вариант):

    * [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)

### 6) Проверка Prometheus

Если мониторинг поднят через `docker-compose` (папка `monitoring`):

```bash
cd monitoring
docker compose up -d
```

Проверить таргеты:

* [http://localhost:9090/targets](http://localhost:9090/targets)

Ожидаемый результат: `contact-crawler` в статусе **UP**.

### 7) Проверка кастомных метрик краулера (`crawler_*`)

Открыть Prometheus-экспорт:

* [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)

Быстрая проверка в терминале (должны быть строки `crawler_...`):

```bash
curl -s http://localhost:8080/actuator/prometheus | grep crawler_
```

Ожидаемый результат: есть метрики вида:

* `crawler_parse_success_total`, `crawler_parse_error_total`
* `crawler_db_saved_total`
* `crawler_parse_seconds_*` (таймер/гистограмма времени парсинга)
* `crawler_parse_error_reason_total{reason="..."}` (ошибки по причинам)




    

