package org.example;

import com.google.gson.*;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class WikipediaArticleSearch {

    private static final String WIKIPEDIA_API_URL = "https://ru.wikipedia.org/w/api.php";
    private static final String WIKIPEDIA_PAGE_URL = "https://ru.wikipedia.org/w/index.php?curid=";
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        boolean isContinue = true;
        while (isContinue) {
            System.out.print("1. Поиск статьи\n2. Выход из программы\n");
            String number = scanner.nextLine().trim();
            switch (number) {
                case "1":
                    try {
                        System.out.print("Введите название статьи: ");
                        String searchQuery = scanner.nextLine().trim();

                        if (searchQuery.isEmpty()) {
                            System.out.println("Запрос не может быть пустым. Попробуйте снова.");
                            continue;
                        } else {
                            List<SearchResult> searchResults = searchWikipedia(searchQuery);
                            if (searchResults.isEmpty()) {
                                System.out.println("По вашему запросу ничего не найдено.");
                                continue;
                            }

                            System.out.println("Результаты поиска:\n");
                            // ИСПРАВЛЕНО: было i <= searchResults.size()
                            for (int i = 0; i < searchResults.size(); i++) {
                                SearchResult result = searchResults.get(i);
                                System.out.printf("%d. %s\n", i + 1, result.getTitle());
                                System.out.println("   " + result.getSnippet() + "...");
                            }
                            System.out.print("\nВведите номер статьи для открытия (1-" + searchResults.size() + "): ");
                            String choiceInput = scanner.nextLine().trim();

                            try {
                                int choice = Integer.parseInt(choiceInput);
                                if (choice >= 1 && choice <= searchResults.size()) {
                                    SearchResult selectedResult = searchResults.get(choice - 1);
                                    openArticleInBrowser(selectedResult.getPageId());
                                    System.out.println("Открываю статью: " + selectedResult.getTitle());
                                } else {
                                    System.out.println("Неверный номер. Попробуйте снова.");
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Пожалуйста, введите корректный номер.");
                            }
                        }
                    } catch (IOException e) {
                        if (e.getMessage().contains("403")) {
                            System.err.println("Ошибка доступа (403): Сервер отклонил запрос. Возможно, требуется User-Agent.");
                        } else if (e.getMessage().contains("404")) {
                            System.err.println("Ошибка: Страница не найдена (404). Проверьте правильность запроса.");
                        } else if (e.getMessage().contains("500")) {
                            System.err.println("Ошибка: Проблема на стороне сервера Википедии (500).");
                        } else if (e.getMessage().contains("timed out")) {
                            System.err.println("Ошибка: Превышено время ожидания ответа от сервера.");
                        } else {
                            System.err.println("Ошибка сети: " + e.getMessage());
                        }
                    } catch (JsonSyntaxException e) {
                        System.err.println("Ошибка: Не удалось обработать ответ от сервера. Некорректный формат данных.");
                    } catch (URISyntaxException e) {
                        System.err.println("Ошибка: Неправильный формат ссылки на статью.");
                    } catch (IllegalArgumentException e) {
                        System.err.println("Ошибка: " + e.getMessage());
                    } catch (Exception e) {
                        System.err.println("Неизвестная ошибка: " + e.getMessage());
                        e.printStackTrace(); // Для отладки
                    }
                    break;

                case "2":
                    isContinue = false;
                    System.out.println("Программа завершена.");
                    break;

                default:
                    System.out.println("Неверный выбор. Введите 1 или 2.");
                    break;
            }
        }
        scanner.close();
    }

    private static List<SearchResult> searchWikipedia(String query) throws Exception {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());

        String urlString = String.format(
                "%s?action=query&list=search&utf8=&format=json&srsearch=%s&srlimit=10",
                WIKIPEDIA_API_URL, encodedQuery
        );

        System.out.println("Отправка запроса: " + urlString); // Для отладки

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        connection.setRequestProperty("User-Agent", "WikipediaSearchApp/1.0");

        int responseCode = connection.getResponseCode();
        System.out.println("Код ответа: " + responseCode); // Для отладки

        if (responseCode != 200) {
            BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            errorReader.close();

            throw new RuntimeException("Ошибка HTTP " + responseCode + ": " + errorResponse.toString());
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        return parseSearchResults(response.toString());
    }

    private static List<SearchResult> parseSearchResults(String jsonResponse) throws IOException { // ИСПРАВЛЕНО: было jsonFile
        List<SearchResult> results = new ArrayList<>();

        try {
            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonObject query = jsonObject.getAsJsonObject("query");
            JsonArray search = query.getAsJsonArray("search");

            for (int i = 0; i < search.size(); i++) {
                JsonObject item = search.get(i).getAsJsonObject();

                int pageId = item.get("pageid").getAsInt();
                String title = item.get("title").getAsString();
                String snippet = cleanSnippet(item.get("snippet").getAsString());

                results.add(new SearchResult(pageId, title, snippet));
            }
        } catch (IllegalStateException | NullPointerException e) {
            throw new JsonSyntaxException("Некорректный формат JSON ответа: " + e.getMessage(), e);
        }

        return results;
    }

    private static String cleanSnippet(String snippet) {
        String cleaned = snippet.replaceAll("<[^>]+>", "");
        cleaned = cleaned.replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ");
        return cleaned.trim();
    }

    private static void openArticleInBrowser(int pageId) {
        try {
            String articleUrl = WIKIPEDIA_PAGE_URL + pageId;
            URI uri = new URI(articleUrl);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            } else {
                System.out.println("Не удалось открыть браузер. Ссылка на статью: " + articleUrl);
            }
        } catch (Exception e) {
            System.out.println("Ошибка при открытии браузера: " + e.getMessage());
        }
    }

    private static class SearchResult {
        private final int pageId;
        private final String title;
        private final String snippet;

        public SearchResult(int pageId, String title, String snippet) {
            this.pageId = pageId;
            this.title = title;
            this.snippet = snippet;
        }

        public int getPageId() { return pageId; }
        public String getTitle() { return title; }
        public String getSnippet() { return snippet; }
    }
}