package com.epubtranslator;

import com.epubtranslator.core.EpubService;
import com.epubtranslator.ui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            // Включаем современную темную тему!
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            EpubService epubService = new EpubService();
            // Передаем пустую строку, MainFrame сам запросит ключ
            MainFrame mainFrame = new MainFrame(epubService);
            mainFrame.setLocationRelativeTo(null);
            mainFrame.setVisible(true);
        });
    }
}