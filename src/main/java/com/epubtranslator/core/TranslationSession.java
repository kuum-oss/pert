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

    private String glossary = "";
    private String styleInstruction = "Художественный";

    public TranslationSession(List<File> files) {
        this.files = files;
        loadNextFile();
    }

    // НОВЫЙ МЕТОД ДЛЯ ВОССТАНОВЛЕНИЯ СЕССИИ
    public void restoreProgress(int fileIdx, int chunkIdx) {
        this.curFileIdx = fileIdx;
        loadNextFile();
        this.curChunkIdx = chunkIdx;
    }

    public void updateSettings(String glossary, String style) {
        this.glossary = glossary;
        this.styleInstruction = style;
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

    public String getPromptChunk() {
        String finalPrompt = "Ты — профессиональный локализатор книг. Стиль перевода: " + styleInstruction + ".\n";
        if (glossary != null && !glossary.trim().isEmpty()) {
            finalPrompt += "ИСПОЛЬЗУЙ СЛЕДУЮЩИЙ ГЛОССАРИЙ (Имена и термины должны переводиться строго так):\n" + glossary + "\n";
        }
        finalPrompt += "СТРОГО ЗАПРЕЩЕНО: 1. Изменять ссылки, атрибуты xmlns, href, src. 2. Добавлять [ ] или ( ) вокруг ссылок. 3. Менять структуру тегов <head> и <html>.\n";
        finalPrompt += "Выдай ТОЛЬКО исправленный HTML код без комментариев.\n\nТекст для перевода:\n";
        return finalPrompt + chunks.get(curChunkIdx);
    }

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
    public int getCurChunkIdx() { return curChunkIdx; } // Геттер чанка
    public int getTotal() { return files.size(); }
}