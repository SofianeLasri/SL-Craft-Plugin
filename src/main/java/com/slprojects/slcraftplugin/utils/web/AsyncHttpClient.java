package com.slprojects.slcraftplugin.utils.web;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Client HTTP asynchrone
 */
public class AsyncHttpClient {

    /**
     * Effectue une requête GET
     * @param urlString URL
     * @return Réponse
     */
    public CompletableFuture<String> get(String urlString) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);

                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                in.close();
                con.disconnect();
                return response.toString();
            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
        });
    }

    /**
     * Effectue une requête GET avec des headers
     * @param urlString URL
     * @param postDataBuilder Données POST
     * @param headers Headers
     * @return Réponse
     */
    public CompletableFuture<String> post(String urlString, PostDataBuilder postDataBuilder, Map<String, String> headers) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);

                // Set request headers
                headers.forEach(con::setRequestProperty);

                con.setDoOutput(true);

                // Write the request body
                try (OutputStream os = con.getOutputStream()) {
                    byte[] input = postDataBuilder.build().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                in.close();
                con.disconnect();
                return response.toString();
            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
        });
    }
}
