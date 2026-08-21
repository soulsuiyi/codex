package com.archive.core.service.impl;

import com.archive.core.service.WatermarkService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 动态水印实现。
 */
@Service
public class WatermarkServiceImpl implements WatermarkService {

    private static final Color WATERMARK_COLOR = new Color(255, 0, 0, 100);

    @Value("${archive.watermark.font-path:C:/Windows/Fonts/simhei.ttf}")
    private String watermarkFontPath;

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null
                && (mimeType.startsWith("image/") || "application/pdf".equals(mimeType));
    }

    @Override
    public InputStream watermarkImage(InputStream source, String mimeType, String text) {
        try (InputStream in = source) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IllegalStateException("无法解析图片内容");
            }
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setColor(WATERMARK_COLOR);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(16, image.getWidth() / 25)));
                FontMetrics metrics = graphics.getFontMetrics();
                graphics.rotate(Math.toRadians(-30), image.getWidth() / 2d, image.getHeight() / 2d);
                int step = metrics.getHeight() * 3;
                for (int x = -image.getHeight(); x < image.getWidth() + metrics.stringWidth(text);
                     x += step + metrics.stringWidth(text)) {
                    for (int y = 0; y < image.getHeight() + metrics.getHeight(); y += step) {
                        graphics.drawString(text, x, y);
                    }
                }
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String format = "png";
            if ("image/jpeg".equals(mimeType) || "image/jpg".equals(mimeType)) {
                format = "jpg";
            }
            if (!ImageIO.write(image, format, out)) {
                ImageIO.write(image, "png", out);
            }
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("图片水印合成失败", e);
        }
    }

    @Override
    public InputStream watermarkPdf(InputStream source, String text) {
        try (PDDocument document = PDDocument.load(source)) {
            for (PDPage page : document.getPages()) {
                try (PDPageContentStream contentStream = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    float width = page.getMediaBox().getWidth();
                    float height = page.getMediaBox().getHeight();
                    contentStream.beginText();
                    contentStream.setNonStrokingColor(WATERMARK_COLOR);
                    contentStream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(-30), width / 2, height / 2));
                    PDType0Font cjkFont = loadCjkFont(document);
                    if (cjkFont != null) {
                        contentStream.setFont(cjkFont, 16);
                        contentStream.showText(text);
                    } else {
                        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 20);
                        contentStream.showText(text.replaceAll("[^\\x00-\\xFF]", "_"));
                    }
                    contentStream.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("PDF 水印合成失败", e);
        }
    }

    private PDType0Font loadCjkFont(PDDocument document) {
        try {
            Path fontPath = Paths.get(watermarkFontPath);
            if (Files.exists(fontPath)) {
                return PDType0Font.load(document, fontPath.toFile());
            }
        } catch (IOException e) {
            // 字体加载失败则降级为内置字体
        }
        return null;
    }
}
