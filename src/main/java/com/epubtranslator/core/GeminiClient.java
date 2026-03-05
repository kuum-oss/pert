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

    public GeminiClient(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    public String translate(String textWithPrompt) throws IOException {
        // Если 2.0 Flash всё равно будет выдавать 429 слишком часто,
        // можно заменить на "gemini-1.5-flash" — у неё лимиты обычно выше.
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey;
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

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();

        int maxRetries = 5;
        int baseDelay = 20000; // Начинаем с 20 секунд

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                if (response.code() == 429) {
                    // Читаем, что именно нам ответил Google
                    String errorBody = response.body() != null ? response.body().string() : "Пустой ответ";
                    System.out.println("====== ПРИЧИНА ОШИБКИ 429 ======");
                    System.out.println(errorBody);
                    System.out.println("================================");

                    int currentDelay = baseDelay * attempt;
                    System.out.println("Попытка " + attempt + ". Ждем " + (currentDelay/1000) + " сек...");
                    Thread.sleep(currentDelay);
                    continue;
                }
                // Внутри метода translate, после проверки 429
                if (!response.isSuccessful()) {
                    String errorDetail = response.body() != null ? response.body().string() : "";
                    if (response.code() == 400) {
                        throw new IOException("Неверный запрос (400). Проверьте API Key или формат текста.");
                    } else if (response.code() == 403) {
                        throw new IOException("Доступ запрещен (403). Ваш ключ не имеет прав или заблокирован.");
                    } else {
                        throw new IOException("Ошибка API: " + response.code() + " " + response.message());
                    }
                }

                if (!response.isSuccessful()) {
                    throw new IOException("Ошибка API: " + response.code() + " " + response.message());
                }

                String responseString = response.body().string();
                JsonObject root = gson.fromJson(responseString, JsonObject.class);
                return root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                        .getAsJsonObject("content").getAsJsonArray("parts")
                        .get(0).getAsJsonObject().get("text").getAsString();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Перевод прерван");
            }
        }
        throw new IOException("Превышено количество попыток. Google слишком загружен. Подождите пару минут и попробуйте снова.");
    }
}