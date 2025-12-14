package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
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
                        }

                        List<SearchResult> searchResults = searchWikipedia(searchQuery);

                        if (searchResults.isEmpty()) {
                            System.out.println("По вашему запросу ничего не найдено.");
                            continue;
                        }

                        System.out.println("Результаты поиска:\n");
                        for (int i = 0; i < searchResults.size(); i++) {
                            SearchResult result = searchResults.get(i);
                            System.out.printf("%d. %s\n", i + 1, result.getTitle());
                            System.out.println("   " + (result.getSnippet().isEmpty() ?
                                    "(нет описания)" : result.getSnippet()) + "...");
                        }

                        boolean articleSelected = false;
                        while (!articleSelected) {
                            System.out.print("\nВведите номер статьи для открытия (1-" +
                                    searchResults.size() + "), или 0 для возврата: ");
                            String choiceInput = scanner.nextLine().trim();

                            try {
                                int choice = Integer.parseInt(choiceInput);

                                if (choice == 0) {
                                    break;
                                } else if (choice < 0 || choice > searchResults.size()) {
                                    System.out.println("Неверный номер. Пожалуйста, введите число от 1 до "
                                            + searchResults.size() + " или 0 для возврата.");
                                } else {
                                    SearchResult selectedResult = searchResults.get(choice - 1);
                                    openArticleInBrowser(selectedResult.getPageId());
                                    System.out.println("Открываю статью: " + selectedResult.getTitle());
                                    articleSelected = true;
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Пожалуйста, введите корректное число.");
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Ошибка: " + e.getMessage());
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

        String urlString = String.format("%s?action=query&list=search&utf8=&format=json&srsearch=%s&srlimit=10",
                WIKIPEDIA_API_URL, encodedQuery);

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "WikipediaSearchApp/1.0");

        int responseCode = connection.getResponseCode();

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("Ошибка HTTP " + responseCode);
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

    private static List<SearchResult> parseSearchResults(String jsonResponse) {
        List<SearchResult> results = new ArrayList<>();

        JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        JsonObject query = jsonObject.getAsJsonObject("query");

        if (!query.has("search")) {
            return results;
        }

        JsonArray search = query.getAsJsonArray("search");

        for (int i = 0; i < search.size(); i++) {
            JsonObject item = search.get(i).getAsJsonObject();

            int pageId = item.get("pageid").getAsInt();
            String title = item.get("title").getAsString();
            String snippet = item.has("snippet") ?
                    cleanSnippet(item.get("snippet").getAsString()) : "";

            results.add(new SearchResult(pageId, title, snippet));
        }

        return results;
    }

    private static String cleanSnippet(String snippet) {
        if (snippet == null || snippet.isEmpty()) {
            return "";
        }

        String cleaned = snippet.replaceAll("<[^>]+>", "");
        cleaned = cleaned.replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ");
        return cleaned.trim();
    }

    private static void openArticleInBrowser(int pageId) throws Exception {
        String articleUrl = WIKIPEDIA_PAGE_URL + pageId;

        if (!Desktop.isDesktopSupported()) {
            throw new Exception("Рабочий стол не поддерживается");
        }

        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            throw new Exception("Открытие браузера не поддерживается");
        }

        URI uri = new URI(articleUrl);
        desktop.browse(uri);
    }

    private static class SearchResult {
        private final int pageId;
        private final String title;
        private final String snippet;

        public SearchResult(int pageId, String title, String snippet) {
            this.pageId = pageId;
            this.title = title != null ? title : "";
            this.snippet = snippet != null ? snippet : "";
        }

        public int getPageId() {
            return pageId;
        }

        public String getTitle() {
            return title;
        }

        public String getSnippet() {
            return snippet;
        }
    }
}