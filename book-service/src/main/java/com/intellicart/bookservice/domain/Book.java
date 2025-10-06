package com.intellicart.bookservice.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "books")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String author;

    @Column(length = 4000)
    private String description;

    private String genre;

    private Double price;

    private Double averageRating;  // added for Goodreads import
}
