package com.slprojects.slcraftplugin.utils.web;

import com.slprojects.slcraftplugin.Main;
import com.slprojects.slcraftplugin.utils.ConsoleLog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Client HTTP asynchrone
 */
public class AsyncHttpClient {

    private final HttpClient httpClient;

    public AsyncHttpClient() {
        this.httpClient = HttpClient.newBuilder().build();
    }

    /**
     * Effectue une requête GET
     *
     * @param urlString URL
     * @return Réponse
     */
    public CompletableFuture<String> get(String urlString) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .header("User-Agent", Main.config.getString("name") + " " + Main.config.getString("version"))
                .header("Server-Type", Main.config.getString("server-type"))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .exceptionally(ex -> {
                    ConsoleLog.danger("Erreur lors de la requête GET vers " + urlString);
                    ConsoleLog.danger(ex.getMessage());
                    return null;
                });
    }

    /**
     * Effectue une requête GET avec des headers
     *
     * @param urlString       URL
     * @param postDataBuilder Données POST
     * @param headers         Headers
     * @return Réponse
     */
    public CompletableFuture<String> post(String urlString, PostDataBuilder postDataBuilder, Map<String, String> headers) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .header("User-Agent", Main.config.getString("name") + " " + Main.config.getString("version"))
                .header("Server-Type", Main.config.getString("server-type"))
                .headers(headers.entrySet().stream()
                        .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()))
                        .toArray(String[]::new))
                .POST(HttpRequest.BodyPublishers.ofString(postDataBuilder.build()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .exceptionally(ex -> {
                    ConsoleLog.danger("Erreur lors de la requête POST vers " + urlString);
                    ConsoleLog.danger(ex.getMessage());
                    return null;
                });
    }
}
