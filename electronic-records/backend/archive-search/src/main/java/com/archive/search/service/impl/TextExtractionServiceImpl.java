package com.archive.search.service.impl;

import com.archive.search.service.TextExtractionService;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * 文本提取实现。
 */
@Service
public class TextExtractionServiceImpl implements TextExtractionService {

    @Value("${archive.ocr.command:tesseract}")
    private String ocrCommand;

    @Override
    public String extractText(InputStream inputStream, String mimeType, String originalName) {
        try {
            if (mimeType != null && mimeType.startsWith("image/")) {
                return ocrWithTesseract(inputStream);
            }
            return extractWithTika(inputStream, originalName);
        } catch (Exception e) {
            return "";
        }
    }

    private String extractWithTika(InputStream inputStream, String originalName) throws Exception {
        try (InputStream in = inputStream) {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            if (originalName != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, originalName);
            }
            parser.parse(in, handler, metadata);
            String text = handler.toString();
            return text == null ? "" : text.trim();
        }
    }

    private String ocrWithTesseract(InputStream inputStream) throws Exception {
        Path tmp = Files.createTempFile("archive-ocr-", ".img");
        try (InputStream in = inputStream) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(ocrCommand, tmp.toString(), "stdout");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
