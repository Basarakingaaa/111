package com.projectcollab.core.service;

import com.projectcollab.core.domain.ProjectDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

@Service
public class ProjectDocumentService {
    private final Path root;

    public ProjectDocumentService(@Value("${app.document-storage-path}") String storagePath) {
        this.root = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public StoredFile store(MultipartFile upload) throws IOException {
        if (upload.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        Files.createDirectories(root);
        String key = java.util.UUID.randomUUID().toString();
        Path target = safePath(key);
        try (InputStream input = upload.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredFile(key, sha256(target), Files.size(target));
    }

    public Path path(ProjectDocument document) {
        Path path = safePath(document.storageKey);
        if (!Files.isRegularFile(path)) throw new IllegalStateException("文档文件不存在或存储不可用");
        return path;
    }

    public String textPreview(ProjectDocument document, int maxChars) {
        if (!isText(document)) return null;
        try (Reader reader = Files.newBufferedReader(path(document), StandardCharsets.UTF_8)) {
            char[] buffer = new char[maxChars];
            int count = reader.read(buffer);
            return count < 0 ? "" : new String(buffer, 0, count);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private boolean isText(ProjectDocument document) {
        String type = document.contentType == null ? "" : document.contentType.toLowerCase();
        String name = document.originalName.toLowerCase();
        return type.startsWith("text/") || type.contains("json") || type.contains("xml") ||
            name.matches(".*\\.(md|txt|json|xml|ya?ml|csv|log|properties|ini)$");
    }

    private Path safePath(String key) {
        Path result = root.resolve(key).normalize();
        if (!result.startsWith(root)) throw new IllegalArgumentException("非法文档存储路径");
        return result;
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path); DigestInputStream dis = new DigestInputStream(input, digest)) {
                dis.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record StoredFile(String storageKey, String sha256, long sizeBytes) {}
}
