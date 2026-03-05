package com.epubtranslator.ui;

import com.epubtranslator.core.EpubService;
import com.epubtranslator.core.GeminiClient;
import com.epubtranslator.core.TranslationSession;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.*;
import java.awt.image.BufferedImage;
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

    private RSyntaxTextArea inputArea, outputArea;
    private JLabel statusLabel, tokenCostLabel, coverLabel;
    private JProgressBar progressBar;
    private DefaultListModel<String> libraryModel;
    private JList<String> libraryList;

    private JButton fullAutoButton, stopButton, aiButton, skipButton, applyButton, exportButton, keyButton;
    private volatile boolean isAutoRunning = false;

    private JComboBox<String> modelBox, styleBox;
    private JTextArea glossaryArea;

    public MainFrame(EpubService epubService) {
        this.epubService = epubService;
        if (!libraryDir.exists()) libraryDir.mkdirs();
        setupUI();
        setupDragAndDrop();
        refreshLibrary();

        // Проверяем, есть ли незаконченная работа с прошлого раза
        checkPreviousSession();
    }

    private void setupUI() {
        setTitle("AI EPUB Translator (Pro IDE)");
        setSize(1400, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- ЦЕНТР (Код) ---
        inputArea = createCodeEditor();
        inputArea.setText("Перетащите книгу сюда.\nКлюч ИИ — по желанию.");
        outputArea = createCodeEditor();

        JPanel leftPanel = createTextPanel("Оригинал + Промпт", inputArea, false);
        JPanel rightPanel = createTextPanel("Перевод / Правки", outputArea, true);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);

        // --- ПРАВО (Библиотека и Обложка) ---
        JPanel rightSidePanel = new JPanel(new BorderLayout());
        rightSidePanel.setPreferredSize(new Dimension(250, 0));

        // Обложка
        coverLabel = new JLabel("Обложка", SwingConstants.CENTER);
        coverLabel.setPreferredSize(new Dimension(250, 350));
        coverLabel.setBorder(BorderFactory.createTitledBorder("🖼 Предпросмотр"));
        rightSidePanel.add(coverLabel, BorderLayout.NORTH);

        // Библиотека
        JPanel libPanel = new JPanel(new BorderLayout());
        libPanel.setBorder(BorderFactory.createTitledBorder("📚 Локальные книги"));
        libraryModel = new DefaultListModel<>();
        libraryList = new JList<>(libraryModel);
        libraryList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) exportSpecificBook(libraryList.getSelectedValue());
            }
        });
        libPanel.add(new JScrollPane(libraryList), BorderLayout.CENTER);
        rightSidePanel.add(libPanel, BorderLayout.CENTER);

        add(rightSidePanel, BorderLayout.EAST);

        // --- ВЕРХ (Настройки) ---
        JPanel topContainer = new JPanel(new BorderLayout());

        JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("⚙️ Настройки ИИ"));

        modelBox = new JComboBox<>(new String[]{"gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash"});
        modelBox.addActionListener(e -> {
            if (geminiClient != null) geminiClient.setModelName((String) modelBox.getSelectedItem());
        });

        styleBox = new JComboBox<>(new String[]{"Художественный", "Технический", "Буквальный", "Адаптированный"});
        // ДОБАВЛЕНО: реагируем на смену стиля
        styleBox.addActionListener(e -> updatePromptPreview());

        glossaryArea = new JTextArea(3, 30);
        glossaryArea.setToolTipText("Пример: Jon Snow=Джон Сноу");
        // ДОБАВЛЕНО: реагируем на ввод текста в глоссарий
        glossaryArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePromptPreview(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePromptPreview(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePromptPreview(); }
        });
        settingsPanel.add(new JLabel("Модель:")); settingsPanel.add(modelBox);
        settingsPanel.add(new JLabel("Стиль:")); settingsPanel.add(styleBox);
        settingsPanel.add(new JLabel("Глоссарий:")); settingsPanel.add(new JScrollPane(glossaryArea));

        JPanel statusKeyPanel = new JPanel(new BorderLayout(10, 5));
        statusKeyPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        statusLabel = new JLabel("Готов к работе");
        progressBar = new JProgressBar(0, 100);

        keyButton = new JButton("🔑 ВВЕСТИ КЛЮЧ AI");
        keyButton.addActionListener(e -> showSecureKeyInputDialog());

        statusKeyPanel.add(statusLabel, BorderLayout.WEST);
        statusKeyPanel.add(progressBar, BorderLayout.CENTER);
        statusKeyPanel.add(keyButton, BorderLayout.EAST);

        topContainer.add(settingsPanel, BorderLayout.CENTER);
        topContainer.add(statusKeyPanel, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

        // --- НИЗ (Кнопки и Токены) ---
        JPanel botPanel = new JPanel(new BorderLayout());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        fullAutoButton = new JButton("🚀 АВТО"); stopButton = new JButton("⏹ СТОП");
        aiButton = new JButton("✨ AI ПЕРЕВОД"); skipButton = new JButton("⏭ ОРИГИНАЛ");
        applyButton = new JButton("✅ ПРИМЕНИТЬ"); exportButton = new JButton("💾 СОХРАНИТЬ КНИГУ");

        fullAutoButton.addActionListener(e -> { if(ensureKey()) startAuto(); });
        aiButton.addActionListener(e -> { if(ensureKey()) manualAi(); });
        stopButton.addActionListener(e -> isAutoRunning = false);
        skipButton.addActionListener(e -> { if(session!=null) outputArea.setText(session.getRawChunk()); });
        applyButton.addActionListener(e -> applyAndNext());
        exportButton.addActionListener(e -> export());

        buttonPanel.add(fullAutoButton); buttonPanel.add(stopButton); buttonPanel.add(new JSeparator(SwingConstants.VERTICAL));
        buttonPanel.add(aiButton); buttonPanel.add(skipButton); buttonPanel.add(applyButton); buttonPanel.add(new JSeparator(SwingConstants.VERTICAL));
        buttonPanel.add(exportButton);

        // Индикатор цены и токенов
        tokenCostLabel = new JLabel("Токены: 0 | Прим. стоимость: $0.00", SwingConstants.RIGHT);
        tokenCostLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15));
        tokenCostLabel.setForeground(new Color(150, 150, 150));

        botPanel.add(buttonPanel, BorderLayout.CENTER);
        botPanel.add(tokenCostLabel, BorderLayout.SOUTH);
        add(botPanel, BorderLayout.SOUTH);

        setControls(false);
    }

    private RSyntaxTextArea createCodeEditor() {
        RSyntaxTextArea textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_HTML);
        textArea.setCodeFoldingEnabled(true); textArea.setLineWrap(true);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        try {
            Theme theme = Theme.load(getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
            theme.apply(textArea);
        } catch (Exception e) {
            textArea.setBackground(new Color(43, 43, 43)); textArea.setForeground(new Color(230, 230, 230));
        }
        return textArea;
    }

    private JPanel createTextPanel(String title, RSyntaxTextArea area, boolean withSearch) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(new RTextScrollPane(area), BorderLayout.CENTER);
        if (withSearch) {
            JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            JTextField searchField = new JTextField(10); JButton searchBtn = new JButton("Найти");
            JTextField replaceField = new JTextField(10); JButton replaceBtn = new JButton("Заменить");

            searchBtn.addActionListener(e -> {
                SearchContext context = new SearchContext(); context.setSearchFor(searchField.getText());
                context.setMatchCase(false); context.setSearchForward(true);
                if (!SearchEngine.find(area, context).wasFound()) JOptionPane.showMessageDialog(this, "Текст не найден");
            });

            replaceBtn.addActionListener(e -> {
                SearchContext context = new SearchContext(); context.setSearchFor(searchField.getText());
                context.setReplaceWith(replaceField.getText()); context.setMatchCase(false); context.setSearchForward(true);
                SearchEngine.replace(area, context);
            });
            searchPanel.add(new JLabel("Поиск:")); searchPanel.add(searchField); searchPanel.add(searchBtn);
            searchPanel.add(new JLabel("Замена:")); searchPanel.add(replaceField); searchPanel.add(replaceBtn);
            p.add(searchPanel, BorderLayout.NORTH);
        }
        JButton copyBtn = new JButton("📋 Копировать текст");
        copyBtn.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(area.getText()), null));
        p.add(copyBtn, BorderLayout.SOUTH);
        return p;
    }

    private void showSecureKeyInputDialog() {
        JPasswordField pf = new JPasswordField();
        int okCxl = JOptionPane.showConfirmDialog(this, pf, "Введите Gemini API Key:", JOptionPane.OK_CANCEL_OPTION);
        if (okCxl == JOptionPane.OK_OPTION) {
            String key = new String(pf.getPassword()).trim();
            if (!key.isEmpty()) {
                this.apiKey = key;
                this.geminiClient = new GeminiClient(apiKey);
                this.geminiClient.setModelName((String) modelBox.getSelectedItem());
                statusLabel.setText("✅ AI Функции активны");
                keyButton.setText("🔑 КЛЮЧ ОК");
                keyButton.setBackground(new Color(60, 130, 60));
            }
        }
    }

    private boolean ensureKey() {
        if (apiKey.isEmpty()) {
            if (JOptionPane.showConfirmDialog(this, "Нужен API Key. Ввести сейчас?", "Нужен ключ", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                showSecureKeyInputDialog();
                return !apiKey.isEmpty();
            }
            return false;
        }
        return true;
    }

    private String cleanResponse(String res) {
        return res.replaceAll("```html|```", "").replaceAll("\\[http", "http").replaceAll("xhtml\\]", "xhtml").replaceAll("\\(http", "http").replaceAll("xhtml\\)", "xhtml").trim();
    }

    private void startAuto() {
        if (session == null) return;
        isAutoRunning = true; setControls(false); stopButton.setEnabled(true);
        session.updateSettings(glossaryArea.getText(), (String) styleBox.getSelectedItem());

        new Thread(() -> {
            try {
                while (isAutoRunning && session.hasMore()) {
                    String prompt = session.getPromptChunk();
                    SwingUtilities.invokeLater(() -> { inputArea.setText(prompt); outputArea.setText("Запрос к ИИ..."); });
                    try {
                        String res = cleanResponse(geminiClient.translate(prompt));
                        SwingUtilities.invokeLater(() -> {
                            outputArea.setText(res);
                            session.apply(res);
                            updateProgress();
                        });
                    } catch (IOException e) {
                        isAutoRunning = false;
                        SwingUtilities.invokeLater(() -> {
                            outputArea.setText("ОШИБКА: " + e.getMessage());
                            JOptionPane.showMessageDialog(this, "Ошибка API:\n" + e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                        });
                        break;
                    }
                    Thread.sleep(8500);
                }
            } catch (Exception e) { e.printStackTrace(); }
            finally { isAutoRunning = false; SwingUtilities.invokeLater(() -> setControls(true)); }
        }).start();
    }

    private void manualAi() {
        if (session == null) return;
        session.updateSettings(glossaryArea.getText(), (String) styleBox.getSelectedItem());
        new Thread(() -> {
            try {
                String res = cleanResponse(geminiClient.translate(session.getPromptChunk()));
                SwingUtilities.invokeLater(() -> { outputArea.setText(res); updateTokens(); });
            } catch (Exception e) { JOptionPane.showMessageDialog(this, "Ошибка AI: " + e.getMessage()); }
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
        saveSessionState(); // АВТОСОХРАНЕНИЕ СЕССИИ

        if (session.hasMore()) {
            inputArea.setText(session.getPromptChunk());
            outputArea.setText("");
        } else {
            statusLabel.setText("ВСЕ ГЛАВЫ ОБРАБОТАНЫ!");
            new File("epub_workspace/progress.txt").delete(); // Удаляем сейв при успешном завершении
        }
        updateTokens();
    }
    // Мгновенное обновление текста промпта в левом окне
    private void updatePromptPreview() {
        if (session != null) {
            // Передаем новые настройки в сессию
            session.updateSettings(glossaryArea.getText(), (String) styleBox.getSelectedItem());
            // Обновляем левое текстовое поле
            inputArea.setText(session.getPromptChunk());
        }
    }

    private void updateTokens() {
        if (geminiClient != null) {
            int tokens = geminiClient.getTotalTokens();
            // Расчет: Примерно $0.35 за 1 миллион токенов (среднее между input и output для Flash)
            double cost = (tokens / 1_000_000.0) * 0.35;
            tokenCostLabel.setText(String.format("Токены: %,d | Прим. стоимость: $%.4f", tokens, cost));
        }
    }

    // --- ЛОГИКА СОХРАНЕНИЯ СЕССИИ ---
    private void saveSessionState() {
        if (session != null && originalFile != null) {
            try {
                String state = originalFile.getAbsolutePath() + "\n" + session.getCurIdx() + "\n" + session.getCurChunkIdx();
                Files.writeString(new File("epub_workspace/progress.txt").toPath(), state);
            } catch (Exception ignored) {}
        }
    }

    private void checkPreviousSession() {
        File progFile = new File("epub_workspace/progress.txt");
        if (progFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(progFile.toPath());
                if (lines.size() >= 3) {
                    File orig = new File(lines.get(0));
                    int fIdx = Integer.parseInt(lines.get(1));
                    int cIdx = Integer.parseInt(lines.get(2));

                    int choice = JOptionPane.showConfirmDialog(this, "Найдена незаконченная книга:\n" + orig.getName() + "\nПродолжить с прерванного места?", "Восстановление сессии", JOptionPane.YES_NO_OPTION);
                    if (choice == JOptionPane.YES_OPTION) {
                        originalFile = orig;
                        session = new TranslationSession(epubService.getExistingHtmlFiles());
                        session.restoreProgress(fIdx, cIdx);
                        setControls(true);
                        updateProgress();
                        displayCover();
                        return; // Выходим, чтобы не очистить воркспейс
                    }
                }
            } catch (Exception ignored) {}
        }
        epubService.cleanup(); // Если отказались или нет сохранения — чистим
    }

    // --- ЛОГИКА ОБЛОЖКИ ---
    private void displayCover() {
        File coverImg = epubService.findCoverImage();
        if (coverImg != null && coverImg.exists()) {
            try {
                BufferedImage img = ImageIO.read(coverImg);
                Image scaled = img.getScaledInstance(230, 330, Image.SCALE_SMOOTH);
                coverLabel.setIcon(new ImageIcon(scaled));
                coverLabel.setText(""); // Убираем текст
            } catch (Exception e) { coverLabel.setText("Обложка не загрузилась"); }
        } else {
            coverLabel.setIcon(null);
            coverLabel.setText("Обложка не найдена");
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
            JOptionPane.showMessageDialog(this, "Готово! Книга сохранена.");
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
            originalFile = f;
            session = new TranslationSession(epubService.extractEpub(f));
            setControls(true);
            updateProgress();
            displayCover(); // ЗАГРУЖАЕМ ОБЛОЖКУ
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