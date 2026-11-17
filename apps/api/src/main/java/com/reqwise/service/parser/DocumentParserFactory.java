package com.reqwise.service.parser;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;

    public DocumentParserFactory(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public DocumentParser getParser(String fileType) {
        return parsers.stream()
                .filter(p -> p.supports(fileType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported document format: " + fileType));
    }
}
