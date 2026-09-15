package com.tabletennis.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * 文档文本解析：基于 Apache Tika 自动检测并提取 txt/md/pdf/docx 等文本类文档
 */
@Slf4j
@Service
public class DocParserService {
    private final AutoDetectParser tikaParser = new AutoDetectParser();

    public String parseFile(String filePath) throws Exception {
        return parseFile(Path.of(filePath), filePath);
    }

    public String parseFile(Path path, String displayName) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        if (displayName != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, displayName);
        }
        try (var is = java.nio.file.Files.newInputStream(path)) {
            tikaParser.parse(is, handler, metadata, new ParseContext());
        }
        return handler.toString();
    }
}
