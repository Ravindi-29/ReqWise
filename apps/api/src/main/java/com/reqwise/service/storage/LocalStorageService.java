package com.reqwise.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorageService.class);
    private final Path rootLocation;

    public LocalStorageService(@Value("${reqwise.storage.base-dir:storage_data}") String baseDir) {
        // Resolve absolute path or relative to project root
        Path resolved = Paths.get(baseDir);
        if (!resolved.isAbsolute()) {
            // Check if current directory or parent directory has storage_data
            if (Files.exists(Paths.get("../../").resolve(baseDir))) {
                resolved = Paths.get("../../").resolve(baseDir).normalize().toAbsolutePath();
            } else {
                resolved = Paths.get(".").resolve(baseDir).normalize().toAbsolutePath();
            }
        }
        this.rootLocation = resolved;
        init();
    }

    private void init() {
        try {
            Files.createDirectories(rootLocation.resolve("raw_documents"));
            Files.createDirectories(rootLocation.resolve("previews"));
            Files.createDirectories(rootLocation.resolve("exports"));
            logger.info("LocalStorageService initialized at: {}", rootLocation);
        } catch (IOException e) {
            logger.error("Could not initialize local storage directories", e);
        }
    }

    @Override
    public String storeFile(InputStream inputStream, String subDirectory, String fileName) {
        try {
            Path targetDir = rootLocation.resolve(subDirectory).normalize();
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            String sanitizedName = UUID.randomUUID() + "_" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path destination = targetDir.resolve(sanitizedName).normalize();
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            return subDirectory + "/" + sanitizedName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file " + fileName, e);
        }
    }

    @Override
    public Path loadFile(String relativePath) {
        Path file = rootLocation.resolve(relativePath).normalize();
        if (!Files.exists(file)) {
            throw new RuntimeException("File not found: " + relativePath);
        }
        return file;
    }

    @Override
    public boolean deleteFile(String relativePath) {
        try {
            Path file = rootLocation.resolve(relativePath).normalize();
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("Failed to delete file: {}", relativePath, e);
            return false;
        }
    }

    @Override
    public Path getStorageRoot() {
        return rootLocation;
    }
}
