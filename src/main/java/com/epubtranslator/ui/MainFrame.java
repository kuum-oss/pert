package com.epubtranslator.ui;

import com.epubtranslator.core.EpubService;
import com.epubtranslator.core.GeminiClient;
import com.epubtranslator.core.TranslationSession;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class MainFrame extends JFrame {

    private final EpubService epubService;
    private GeminiClient geminiClient;
    private String apiKey = "";

    private TranslationSession session;
    private File originalFile;
    private final File libraryDir = new File("Saved_EPUBs");

    private JTextArea inputArea, outputArea;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private DefaultListModel<String> libraryModel;
    private JList<String> libraryList;

    private JButton fullAutoButton, stopButton, aiButton, skipButton, applyButton, exportButton, keyButton;
    private volatile boolean isAutoRunning = false;

    public MainFrame(EpubService epubService) {
        this.epubService = epubService;
        if (!libraryDir.exists()) libraryDir.mkdirs();
        setupUI();
        setupDragAndDrop();
        refreshLibrary();

        Runtime.getRuntime().addShutdownHook(new Thread(epubService::cleanup));
    }

    private void setupUI() {
        setTitle("AI EPUB Translator (Flexible Mode)");
        setSize(1300, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Текстовые зоны
        inputArea = new JTextArea("Перетащите книгу сюда.\nКлюч ИИ — по желанию (для функций авто-перевода).");
        inputArea.setLineWrap(true);
        outputArea = new JTextArea();
        outputArea.setLineWrap(true);

        JPanel leftPanel = createTextPanel("Оригинал", inputArea);
        JPanel rightPanel = createTextPanel("Перевод / Правки", outputArea);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);

        // Библиотека
        JPanel libPanel = new JPanel(new BorderLayout());
        libPanel.setBorder(BorderFactory.createTitledBorder("📚 Локальные книги"));
        libPanel.setPreferredSize(new Dimension(250, 0));
        libraryModel = new DefaultListModel<>();
        libraryList = new JList<>(libraryModel);
        libraryList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) exportSpecificBook(libraryList.getSelectedValue());
            }
        });
        libPanel.add(new JScrollPane(libraryList), BorderLayout.CENTER);
        add(libPanel, BorderLayout.EAST);

        // Статус и Ключ
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        statusLabel = new JLabel("Режим: Ручной перевод (без ключа)");
        progressBar = new JProgressBar(0, 100);

        keyButton = new JButton("🔑 ВВЕСТИ КЛЮЧ AI");
        keyButton.addActionListener(e -> showSecureKeyInputDialog());

        topPanel.add(statusLabel, BorderLayout.WEST);
        topPanel.add(progressBar, BorderLayout.CENTER);
        topPanel.add(keyButton, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Кнопки управления
        JPanel botPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        fullAutoButton = new JButton("🚀 АВТО");
        stopButton = new JButton("⏹ СТОП");
        aiButton = new JButton("✨ AI ПЕРЕВОД");
        skipButton = new JButton("⏭ ВСТАВИТЬ ОРИГИНАЛ");
        applyButton = new JButton("✅ ПРИМЕНИТЬ И ДАЛЕЕ");
        exportButton = new JButton("💾 СОХРАНИТЬ КНИГУ");

        // Логика: если ключа нет, кнопка предложит его ввести, но не заблокирует работу
        fullAutoButton.addActionListener(e -> { if(ensureKey()) startAuto(); });
        aiButton.addActionListener(e -> { if(ensureKey()) manualAi(); });

        // Эти кнопки работают ВСЕГДА (даже без ключа)
        stopButton.addActionListener(e -> isAutoRunning = false);
        skipButton.addActionListener(e -> { if(session!=null) outputArea.setText(session.getRawChunk()); });
        applyButton.addActionListener(e -> applyAndNext());
        exportButton.addActionListener(e -> export());

        botPanel.add(fullAutoButton); botPanel.add(stopButton);
        botPanel.add(new JSeparator(SwingConstants.VERTICAL));
        botPanel.add(aiButton); botPanel.add(skipButton); botPanel.add(applyButton);
        botPanel.add(new JSeparator(SwingConstants.VERTICAL));
        botPanel.add(exportButton);
        add(botPanel, BorderLayout.SOUTH);

        setControls(false);
    }

    private void showSecureKeyInputDialog() {
        JPasswordField pf = new JPasswordField();
        int okCxl = JOptionPane.showConfirmDialog(this, pf, "Введите Gemini API Key (для функций ИИ):", JOptionPane.OK_CANCEL_OPTION);

        if (okCxl == JOptionPane.OK_OPTION) {
            String key = new String(pf.getPassword()).trim();
            if (!key.isEmpty()) {
                this.apiKey = key;
                this.geminiClient = new GeminiClient(apiKey);
                statusLabel.setText("✅ AI Функции активированы");
                keyButton.setText("🔑 КЛЮЧ ОК");
                keyButton.setBackground(new Color(200, 255, 200));
            }
        }
    }

    private boolean ensureKey() {
        if (apiKey.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this, "Для этой функции нужен API Key. Ввести сейчас?", "Нужен ключ", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                showSecureKeyInputDialog();
                return !apiKey.isEmpty();
            }
            return false;
        }
        return true;
    }

    // --- Остальные методы ---

    private JPanel createTextPanel(String title, JTextArea area) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(new JScrollPane(area), BorderLayout.CENTER);
        JButton copyBtn = new JButton("📋 Копировать");
        copyBtn.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(area.getText()), null));
        p.add(copyBtn, BorderLayout.SOUTH);
        return p;
    }

    private String cleanResponse(String res) {
        return res.replaceAll("```html|```", "").replaceAll("\\[http", "http").replaceAll("xhtml\\]", "xhtml").replaceAll("\\(http", "http").replaceAll("xhtml\\)", "xhtml").trim();
    }

    private void startAuto() {
        if (session == null) return;
        isAutoRunning = true;
        setControls(false);
        stopButton.setEnabled(true);
        new Thread(() -> {
            try {
                while (isAutoRunning && session.hasMore()) {
                    String prompt = session.getPromptChunk();
                    SwingUtilities.invokeLater(() -> {
                        inputArea.setText(prompt);
                        outputArea.setText("Запрос к ИИ...");
                    });

                    try {
                        // Пытаемся перевести
                        String res = cleanResponse(geminiClient.translate(prompt));
                        SwingUtilities.invokeLater(() -> {
                            outputArea.setText(res);
                            session.apply(res);
                            updateProgress();
                        });
                    } catch (IOException e) {
                        // ЕСЛИ КЛЮЧ НЕВЕРНЫЙ ИЛИ ДРУГАЯ ОШИБКА API
                        isAutoRunning = false; // Останавливаем цикл
                        SwingUtilities.invokeLater(() -> {
                            outputArea.setText("ОШИБКА: " + e.getMessage());
                            JOptionPane.showMessageDialog(this,
                                    "Ошибка API (возможно, неверный ключ):\n" + e.getMessage(),
                                    "Ошибка перевода", JOptionPane.ERROR_MESSAGE);
                        });
                        break; // Выходим из цикла while
                    }

                    Thread.sleep(8500);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isAutoRunning = false;
                SwingUtilities.invokeLater(() -> setControls(true));
            }
        }).start();
    }

    private void manualAi() {
        if (session == null) return;
        new Thread(() -> {
            try {
                String res = cleanResponse(geminiClient.translate(session.getPromptChunk()));
                SwingUtilities.invokeLater(() -> outputArea.setText(res));
            } catch (Exception e) { JOptionPane.showMessageDialog(this, "Ошибка AI: проверьте лимиты или ключ."); }
        }).start();
    }

    private void applyAndNext() {
        if (session != null && !outputArea.getText().isEmpty()) {
            session.apply(outputArea.getText());
            updateProgress();
        }
    }

    private void updateProgress() {
        if (session == null) return;
        int p = (int)(((double)session.getCurIdx() / session.getTotal()) * 100);
        progressBar.setValue(p);
        statusLabel.setText("Глава " + (session.getCurIdx() + 1) + " из " + session.getTotal());
        if (session.hasMore()) {
            inputArea.setText(session.getPromptChunk());
            outputArea.setText("");
        } else {
            statusLabel.setText("ВСЕ ГЛАВЫ ОБРАБОТАНЫ!");
        }
    }

    private void setControls(boolean b) {
        fullAutoButton.setEnabled(b); aiButton.setEnabled(b);
        skipButton.setEnabled(b); applyButton.setEnabled(b); exportButton.setEnabled(b);
        stopButton.setEnabled(!b && isAutoRunning);
    }

    private void refreshLibrary() {
        libraryModel.clear();
        File[] fs = libraryDir.listFiles((d, n) -> n.endsWith(".epub"));
        if (fs != null) for (File f : fs) libraryModel.addElement(f.getName());
    }

    private void export() {
        if (originalFile == null) return;
        try {
            String n = "RU_" + originalFile.getName();
            File libFile = new File(libraryDir, n);
            epubService.packEpub(libFile);
            Files.copy(libFile.toPath(), new File(System.getProperty("user.home") + "/Downloads/" + n).toPath(), StandardCopyOption.REPLACE_EXISTING);
            refreshLibrary();
            JOptionPane.showMessageDialog(this, "Готово! Книга в Загрузках.");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void exportSpecificBook(String fileName) {
        if (fileName == null) return;
        File src = new File(libraryDir, fileName);
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(System.getProperty("user.home") + "/Downloads/" + fileName));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try { Files.copy(src.toPath(), fc.getSelectedFile().toPath(), StandardCopyOption.REPLACE_EXISTING); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void load(File f) {
        try {
            session = new TranslationSession(epubService.extractEpub(f));
            originalFile = f;
            setControls(true);
            updateProgress();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void setupDragAndDrop() {
        new DropTarget(inputArea, new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) load(files.get(0));
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }
}