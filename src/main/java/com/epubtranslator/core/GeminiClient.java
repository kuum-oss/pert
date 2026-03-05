package com.epubtranslator.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GeminiClient {
    private final String apiKey;
    private final OkHttpClient client;
    private final Gson gson = new Gson();

    private String modelName = "gemini-2.0-flash";
    private int totalTokens = 0; // СЧЕТЧИК ТОКЕНОВ

    public GeminiClient(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public String translate(String textWithPrompt) throws IOException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

        JsonObject jsonBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();

        textPart.addProperty("text", textWithPrompt);
        parts.add(textPart);
        contentObj.add("parts", parts);
        contents.add(contentObj);
        jsonBody.add("contents", contents);

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder().url(url).post(body).build();

        int maxAttempts = 3;
        int baseDelay = 5000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                if (response.code() == 429) {
                    Thread.sleep(baseDelay * attempt);
                    continue;
                }

                if (!response.isSuccessful()) {
                    if (response.code() == 400) throw new IOException("Неверный запрос (400). Проверьте API Key.");
                    if (response.code() == 403) throw new IOException("Доступ запрещен (403).");
                    throw new IOException("Ошибка API: " + response.code() + " " + response.message());
                }

                String responseString = response.body().string();
                JsonObject root = gson.fromJson(responseString, JsonObject.class);

                // СОХРАНЯЕМ ПОТРАЧЕННЫЕ ТОКЕНЫ ИЗ ОТВЕТА GOOGLE
                if (root.has("usageMetadata")) {
                    totalTokens += root.getAsJsonObject("usageMetadata").get("totalTokenCount").getAsInt();
                }

                return root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                        .getAsJsonObject("content").getAsJsonArray("parts")
                        .get(0).getAsJsonObject().get("text").getAsString();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Перевод прерван");
            }
        }
        throw new IOException("Превышено количество попыток.");
    }
}