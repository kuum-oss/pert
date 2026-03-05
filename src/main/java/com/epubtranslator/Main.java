package com.epubtranslator;

import com.epubtranslator.core.EpubService;
import com.epubtranslator.core.GeminiClient;
import com.epubtranslator.ui.MainFrame;
import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            EpubService epubService = new EpubService();
            // Передаем пустую строку, MainFrame сам запросит ключ
            MainFrame mainFrame = new MainFrame(epubService);
            mainFrame.setLocationRelativeTo(null);
            mainFrame.setVisible(true);
        });
    }
}