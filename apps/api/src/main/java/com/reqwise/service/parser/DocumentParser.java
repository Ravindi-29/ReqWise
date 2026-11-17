package com.reqwise.service.parser;

import java.nio.file.Path;
import java.util.List;

public interface DocumentParser {
    List<ParsedBlock> parse(Path filePath);
    boolean supports(String fileType);
}
