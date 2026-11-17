package com.reqwise.service.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageService(tempDir.toString());
    }

    @Test
    void shouldStoreAndLoadFile() throws IOException {
        String content = "Test SRS File Content";
        ByteArrayInputStream input = new ByteArrayInputStream(content.getBytes());

        String relativePath = storageService.storeFile(input, "raw_documents", "test_spec.pdf");
        assertNotNull(relativePath);
        assertTrue(relativePath.startsWith("raw_documents/"));

        Path loadedFile = storageService.loadFile(relativePath);
        assertTrue(Files.exists(loadedFile));
        assertEquals(content, Files.readString(loadedFile));

        boolean deleted = storageService.deleteFile(relativePath);
        assertTrue(deleted);
        assertFalse(Files.exists(loadedFile));
    }
}
