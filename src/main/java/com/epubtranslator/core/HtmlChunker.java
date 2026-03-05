package com.epubtranslator.core;

import java.util.ArrayList;
import java.util.List;

public class HtmlChunker {

    private static final int MAX_CHUNK_SIZE = 40000;

    public List<String> splitIntoChunks(String html) {
        List<String> chunks = new ArrayList<>();
        int currentIndex = 0;

        while (currentIndex < html.length()) {
            if (html.length() - currentIndex <= MAX_CHUNK_SIZE) {
                chunks.add(html.substring(currentIndex));
                break;
            }

            int searchEnd = currentIndex + MAX_CHUNK_SIZE;
            int splitIndex = html.lastIndexOf("</p>", searchEnd);

            if (splitIndex <= currentIndex) {
                splitIndex = html.lastIndexOf("</div>", searchEnd);
            }

            if (splitIndex <= currentIndex) {
                splitIndex = html.lastIndexOf(">", searchEnd);
            }

            if (splitIndex <= currentIndex) {
                splitIndex = searchEnd;
            } else {

                if (html.startsWith("</p>", splitIndex)) {
                    splitIndex += 4;
                } else if (html.startsWith("</div>", splitIndex)) {
                    splitIndex += 6;
                } else if (html.startsWith(">", splitIndex)) {
                    splitIndex += 1;
                }
            }

            chunks.add(html.substring(currentIndex, splitIndex));
            currentIndex = splitIndex;
        }

        return chunks;
    }
}