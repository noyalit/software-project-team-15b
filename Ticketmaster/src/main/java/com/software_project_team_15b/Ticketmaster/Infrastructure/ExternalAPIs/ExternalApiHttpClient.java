package com.software_project_team_15b.Ticketmaster.Infrastructure.ExternalAPIs;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public class ExternalApiHttpClient {

    private final String baseUrl;
    private final HttpClient client;

    public ExternalApiHttpClient(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl cannot be null or blank");
        }

        this.baseUrl = baseUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String postForm(Map<String, String> body) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("body cannot be null or empty");
        }

        String encodedBody = encodeBody(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodedBody))
                .build();

        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalApiException(
                        "External API returned HTTP status " + response.statusCode()
                );
            }

            return response.body();

        } catch (IOException e) {
            throw new ExternalApiException("Failed to call external API", e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("External API call was interrupted", e);
        }
    }

    private String encodeBody(Map<String, String> body) {
        return body.entrySet()
                .stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
