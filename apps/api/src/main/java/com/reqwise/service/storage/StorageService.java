package com.reqwise.service.storage;

import java.io.InputStream;
import java.nio.file.Path;

public interface StorageService {
    String storeFile(InputStream inputStream, String subDirectory, String fileName);
    Path loadFile(String relativePath);
    boolean deleteFile(String relativePath);
    Path getStorageRoot();
}
