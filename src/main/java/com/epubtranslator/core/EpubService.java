package com.epubtranslator.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.*;

public class EpubService {
    // Папка-кэш, куда мы распакуем книгу
    private final File workspaceDir = new File("epub_workspace");

    public List<File> extractEpub(File epubFile) throws IOException {
        cleanup(); // Удаляем старый проект
        workspaceDir.mkdirs();
        List<File> htmlFiles = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(epubFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(workspaceDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    Files.copy(zis, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    // Ищем текстовые файлы для ИИ
                    String lowerName = entry.getName().toLowerCase();
                    if (lowerName.endsWith(".html") || lowerName.endsWith(".xhtml") || lowerName.endsWith(".htm")) {
                        htmlFiles.add(newFile);
                    }
                }
            }
        }
        // Сортируем главы по алфавиту/номеру
        htmlFiles.sort(Comparator.comparing(File::getName));
        return htmlFiles;
    }

    public void packEpub(File outputFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
            // 1. mimetype должен быть ПЕРВЫМ и БЕЗ сжатия для валидности EPUB
            File mimeTypeFile = new File(workspaceDir, "mimetype");
            if (mimeTypeFile.exists()) {
                ZipEntry mimeEntry = new ZipEntry("mimetype");
                mimeEntry.setMethod(ZipEntry.STORED);
                mimeEntry.setSize(mimeTypeFile.length());
                mimeEntry.setCrc(calculateCrc(mimeTypeFile));
                zos.putNextEntry(mimeEntry);
                Files.copy(mimeTypeFile.toPath(), zos);
                zos.closeEntry();
            }

            // 2. Упаковываем всё остальное
            Path workspacePath = workspaceDir.toPath();
            try (Stream<Path> paths = Files.walk(workspacePath)) {
                for (Path path : paths.collect(Collectors.toList())) {
                    String fileName = path.getFileName().toString();

                    // ИГНОРИРУЕМ: директории, уже добавленный mimetype и скрытые файлы Mac
                    if (Files.isDirectory(path) ||
                            fileName.equals("mimetype") ||
                            fileName.equals(".DS_Store") ||
                            fileName.startsWith("._")) {
                        continue;
                    }

                    // Формируем путь внутри архива (заменяем обратные слеши на прямые для Windows/Mac совместимости)
                    String zipEntryName = workspacePath.relativize(path).toString().replace("\\", "/");
                    zos.putNextEntry(new ZipEntry(zipEntryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    private long calculateCrc(File file) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(Files.readAllBytes(file.toPath()));
        return crc.getValue();
    }

    public void cleanup() {
        if (workspaceDir.exists()) {
            try (Stream<Path> walk = Files.walk(workspaceDir.toPath())) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException ignored) {}
        }
    }
}