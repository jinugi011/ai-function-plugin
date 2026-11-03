package io.aiplugin.core.rag.document;

import io.aiplugin.core.AiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** PDF / TXT / MD 등 텍스트 문서를 읽습니다. */
public final class DocumentLoader {

    public static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "markdown", "csv", "json", "xml", "html", "log");

    private DocumentLoader() {
    }

    public static boolean isSupported(String fileName) {
        String ext = extension(fileName);
        return ext.equals("pdf") || TEXT_EXTENSIONS.contains(ext);
    }

    public static LoadedDocument load(Path file) {
        try {
            return load(file.toString(), Files.readAllBytes(file));
        } catch (IOException e) {
            throw new AiException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    public static LoadedDocument load(String source, InputStream in) {
        try (in) {
            return load(source, in.readAllBytes());
        } catch (IOException e) {
            throw new AiException("Cannot read " + source + ": " + e.getMessage(), e);
        }
    }

    public static LoadedDocument load(String source, byte[] bytes) {
        String ext = extension(source);
        List<String> pages;
        if (ext.equals("pdf")) {
            pages = readPdf(source, bytes);
        } else if (TEXT_EXTENSIONS.contains(ext)) {
            pages = List.of(decode(bytes));
        } else {
            throw new AiException("Unsupported document type: " + source + " (supported: pdf, " + TEXT_EXTENSIONS + ")");
        }
        return new LoadedDocument(source, pages, sha256(bytes));
    }

    private static List<String> readPdf(String source, byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<String> pages = new ArrayList<>();
            for (int p = 1; p <= pdf.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                pages.add(stripper.getText(pdf));
            }
            return pages;
        } catch (IOException e) {
            throw new AiException("Cannot parse PDF " + source + ": " + e.getMessage(), e);
        }
    }

    /** UTF-8 로 읽고, 깨지면(한글 윈도우 파일 등) MS949 로 다시 시도합니다. */
    private static String decode(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("�")) {
            return utf8.startsWith("﻿") ? utf8.substring(1) : utf8;
        }
        try {
            return new String(bytes, "MS949");
        } catch (java.io.UnsupportedEncodingException e) {
            return utf8;
        }
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
