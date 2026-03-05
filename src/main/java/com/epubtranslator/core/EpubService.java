package com.epubtranslator.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.*;

public class EpubService {
    private final File workspaceDir = new File("epub_workspace");

    public List<File> extractEpub(File epubFile) throws IOException {
        cleanup();
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

                    String lowerName = entry.getName().toLowerCase();
                    if (lowerName.endsWith(".html") || lowerName.endsWith(".xhtml") || lowerName.endsWith(".htm")) {
                        htmlFiles.add(newFile);
                    }
                }
            }
        }
        htmlFiles.sort(Comparator.comparing(File::getName));
        return htmlFiles;
    }

    // НОВЫЙ МЕТОД: Получение уже распакованных файлов (для восстановления сессии)
    public List<File> getExistingHtmlFiles() {
        List<File> htmlFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(workspaceDir.toPath())) {
            paths.map(Path::toFile).filter(f -> {
                String name = f.getName().toLowerCase();
                return f.isFile() && (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm"));
            }).forEach(htmlFiles::add);
        } catch (IOException e) { e.printStackTrace(); }
        htmlFiles.sort(Comparator.comparing(File::getName));
        return htmlFiles;
    }

    // НОВЫЙ МЕТОД: Поиск файла обложки
    public File findCoverImage() {
        if (!workspaceDir.exists()) return null;
        try (Stream<Path> paths = Files.walk(workspaceDir.toPath())) {
            return paths.map(Path::toFile)
                    .filter(f -> f.isFile() && f.getName().toLowerCase().matches(".*(cover|front).*\\.(jpg|jpeg|png)$"))
                    .findFirst().orElse(null);
        } catch (IOException e) { return null; }
    }

    public void packEpub(File outputFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
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

            Path workspacePath = workspaceDir.toPath();
            try (Stream<Path> paths = Files.walk(workspacePath)) {
                for (Path path : paths.collect(Collectors.toList())) {
                    String fileName = path.getFileName().toString();
                    if (Files.isDirectory(path) || fileName.equals("mimetype") || fileName.equals(".DS_Store") || fileName.startsWith("._")) continue;
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