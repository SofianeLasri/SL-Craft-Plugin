package com.slprojects.slcraftplugin.utils.web;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Constructeur de données POST
 */
public class PostDataBuilder {
    private Map<String, String> data;

    public PostDataBuilder() {
        data = new LinkedHashMap<>();
    }

    /**
     * Ajoute une donnée POST
     * @param name Nom
     * @param value Valeur
     */
    public PostDataBuilder addData(String name, String value) {
        data.put(name, value);
        return this;
    }

    /**
     * Construit les données POST
     * @return Données POST
     */
    public String build() {
        String postData = data.entrySet().stream()
                .map(entry -> {
                    try {
                        return entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString());
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("Error encoding POST data.", e);
                    }
                })
                .collect(Collectors.joining("&"));

        return postData;
    }
}
