package com.epubtranslator.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class TranslationSession {
    private final List<File> files;
    private int curFileIdx = 0;
    private int curChunkIdx = 0;
    private List<String> chunks;
    private StringBuilder translatedBuffer = new StringBuilder();
    private final HtmlChunker chunker = new HtmlChunker();

    // В TranslationSession.java измените AI_PROMPT:
    private static final String PROMPT =
            "Ты — профессиональный локализатор книг. Переведи текст внутри HTML на русский язык. " +
                    "СТРОГО ЗАПРЕЩЕНО: " +
                    "1. Изменять ссылки, атрибуты xmlns, href, src. " +
                    "2. Добавлять любые символы [ ] или ( ) вокруг ссылок. " +
                    "3. Менять структуру тегов <head> и <html>. " +
                    "Выдай ТОЛЬКО исправленный код без комментариев.\n\nТекст для перевода:\n";
    public TranslationSession(List<File> files) {
        this.files = files;
        loadNextFile();
    }

    private void loadNextFile() {
        if (curFileIdx < files.size()) {
            try {
                String content = Files.readString(files.get(curFileIdx).toPath(), StandardCharsets.UTF_8);
                chunks = chunker.splitIntoChunks(content);
                curChunkIdx = 0;
                translatedBuffer.setLength(0);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public boolean hasMore() { return curFileIdx < files.size(); }
    public String getPromptChunk() { return PROMPT + chunks.get(curChunkIdx); }
    public String getRawChunk() { return chunks.get(curChunkIdx); }

    public void apply(String translated) {
        translatedBuffer.append(translated).append("\n");
        curChunkIdx++;
        if (curChunkIdx >= chunks.size()) {
            try {
                Files.writeString(files.get(curFileIdx).toPath(), translatedBuffer.toString(), StandardCharsets.UTF_8);
            } catch (Exception e) { e.printStackTrace(); }
            curFileIdx++;
            loadNextFile();
        }
    }

    public int getCurIdx() { return curFileIdx; }
    public int getTotal() { return files.size(); }
}