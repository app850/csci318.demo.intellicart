package com.intellicart.bookservice.bootstrap;

import com.intellicart.bookservice.domain.Book;
import com.intellicart.bookservice.infrastructure.BookRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.books.import.enabled", havingValue = "true", matchIfMissing = true)
public class CsvLoader implements CommandLineRunner {

    private static final int TITLE_MAX  = 1024; // cap to avoid DB length issues
    private static final int AUTHOR_MAX = 1024;

    private final BookRepository repo;
    private final ResourceLoader resources;

    public CsvLoader(BookRepository repo, ResourceLoader resources) {
        this.repo = repo;
        this.resources = resources;
    }

    @Value("${app.books.csv:classpath:data/books.csv}")
    private String csvPath;

    @Value("${app.books.import.limit:0}")
    private int importLimit;

    @Override
    public void run(String... args) throws Exception {
        if (repo.count() > 0) {
            return;
        }

        Resource res = resources.getResource(csvPath);
        if (!res.exists()) {
            return;
        }

        int imported = 0;
        int skipped = 0;

        try (var in = res.getInputStream();
             var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }

            char delimiter = headerLine.contains("\t") ? '\t' : ',';

            String[] rawHeaders = headerLine.split(String.valueOf(delimiter), -1);
            List<String> headerList = new ArrayList<>();
            for (String h : rawHeaders) {
                String trimmed = h == null ? "" : h.trim();
                if (!trimmed.isEmpty()) {
                    headerList.add(trimmed);
                }
            }
            String[] headers = headerList.toArray(new String[0]);

            CSVFormat format = CSVFormat.Builder.create()
                    .setHeader(headers)
                    .setSkipHeaderRecord(false)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setDelimiter(delimiter)
                    .build();

            try (var parser = new CSVParser(reader, format)) {

                for (CSVRecord r : parser) {
                    try {
                        if (importLimit > 0 && imported >= importLimit) break;

                        String titleRaw   = get(r, parser.getHeaderNames(), "title");
                        String authorsRaw = get(r, parser.getHeaderNames(), "authors");
                        String avgStr     = get(r, parser.getHeaderNames(), "average_rating");
                        String lang       = get(r, parser.getHeaderNames(), "language_code");

                        if (isBlank(titleRaw)) { skipped++; continue; }

                        String title  = cap(collapse(titleRaw), TITLE_MAX);
                        String author = cap(primaryAuthor(authorsRaw), AUTHOR_MAX);
                        Double avg    = parseDouble(avgStr);
                        String genre  = (notBlank(lang) ? "Lang:" + lang.trim() : "Unknown");

                        Book b = new Book();
                        b.setTitle(title);
                        b.setAuthor(author);
                        b.setGenre(genre);
                        b.setAverageRating(avg);
                        b.setDescription("Imported from Goodreads");

                        repo.save(b);
                        imported++;
                        if (imported % 1000 == 0) {
                        }
                    } catch (Exception rowEx) {
                        skipped++;
                    }
                }
            }
        }
    }
    private static String get(CSVRecord r, List<String> headers, String want) {
        String v = getRaw(r, want);
        if (v != null) return v;

        String wantNorm = norm(want);
        for (String h : headers) {
            if (isBlank(h)) continue;
            if (norm(h).equals(wantNorm)) {
                return getRaw(r, h);
            }
            if (norm(h).equals("numpages") && wantNorm.equals("numpages")) {
                return getRaw(r, h);
            }
        }
        return null;
    }

    private static String getRaw(CSVRecord r, String header) {
        try {
            String v = r.get(header);
            return v != null ? v.trim() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[\\s_]+", "");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean notBlank(String s) {
        return !isBlank(s);
    }

    private static String collapse(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }

    private static String primaryAuthor(String authors) {
        if (authors == null) return null;
        String a = authors;
        int slash = a.indexOf('/');
        if (slash > 0) a = a.substring(0, slash);
        return a.trim();
    }

    private static String cap(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static Double parseDouble(String s) {
        if (isBlank(s)) return null;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }
}
