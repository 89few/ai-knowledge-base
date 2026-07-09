package org.aiknowledgebase.service;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Service
public class FileTextExtractorService {

    @Value("${ocr.tesseract.data-path:C:/Program Files/Tesseract-OCR/tessdata}")
    private String tessDataPath;

    @Value("${ocr.tesseract.language:chi_sim+eng}")
    private String tessLanguage;

    public String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename();

        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String lowerName = filename.toLowerCase();

        try {
            if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
                return extractPlainText(file);
            }

            if (lowerName.endsWith(".pdf")) {
                return extractPdf(file);
            }

            if (lowerName.endsWith(".docx")) {
                return extractDocx(file);
            }

            if (isImage(lowerName)) {
                return extractImageByOcr(file);
            }

            throw new IllegalArgumentException("暂不支持该文件类型，仅支持 txt、md、pdf、docx、png、jpg、jpeg、bmp");

        } catch (Exception e) {
            throw new RuntimeException("文件解析失败：" + e.getMessage(), e);
        }
    }

    public String getFileType(String filename) {
        if (filename == null) {
            return "unknown";
        }

        String lowerName = filename.toLowerCase();

        if (lowerName.endsWith(".txt")) return "txt";
        if (lowerName.endsWith(".md")) return "md";
        if (lowerName.endsWith(".pdf")) return "pdf";
        if (lowerName.endsWith(".docx")) return "docx";
        if (isImage(lowerName)) return "image";

        return "unknown";
    }

    private String extractPlainText(MultipartFile file) throws Exception {
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    private String extractPdf(MultipartFile file) throws Exception {
        File tempFile = toTempFile(file);

        try (PDDocument document = Loader.loadPDF(tempFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } finally {
            tempFile.delete();
        }
    }

    private String extractDocx(MultipartFile file) throws Exception {
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractImageByOcr(MultipartFile file) throws Exception {
        File tempFile = toTempFile(file);

        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessDataPath);
            tesseract.setLanguage(tessLanguage);

            return tesseract.doOCR(tempFile);
        } finally {
            tempFile.delete();
        }
    }

    private boolean isImage(String lowerName) {
        return lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".bmp");
    }

    private File toTempFile(MultipartFile file) throws Exception {
        String suffix = ".tmp";
        String filename = file.getOriginalFilename();

        if (filename != null && filename.contains(".")) {
            suffix = filename.substring(filename.lastIndexOf("."));
        }

        File tempFile = Files.createTempFile("upload-", suffix).toFile();
        file.transferTo(tempFile);

        return tempFile;
    }
}