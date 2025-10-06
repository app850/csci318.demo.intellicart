package com.intellicart.bookservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.books")
public class BooksImportProperties {
    private String csv = "classpath:data/books.csv";
    private boolean importEnabled = true;

    // getters/setters
}
