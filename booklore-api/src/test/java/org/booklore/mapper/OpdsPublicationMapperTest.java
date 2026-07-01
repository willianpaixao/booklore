package org.booklore.mapper;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.opds.OpdsPublicationMetadata;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpdsPublicationMapperTest {

    private final OpdsPublicationMapper mapper = Mappers.getMapper(OpdsPublicationMapper.class);

    @Test
    void toMetadata_mapsSchemaOrgFields() {
        Book book = Book.builder()
                .id(7L)
                .addedOn(Instant.parse("2025-01-01T12:00:00Z"))
                .metadata(BookMetadata.builder()
                        .title("The Hobbit")
                        .subtitle("There and Back Again")
                        .description("A fantasy novel")
                        .language("en")
                        .publisher("Allen & Unwin")
                        .publishedDate(LocalDate.of(1937, 9, 21))
                        .authors(List.of("J.R.R. Tolkien"))
                        .categories(Set.of("Fantasy"))
                        .isbn13("9780261102217")
                        .build())
                .build();

        OpdsPublicationMetadata metadata = mapper.toMetadata(book);

        assertThat(metadata.type()).isEqualTo("http://schema.org/Book");
        assertThat(metadata.title()).isEqualTo("The Hobbit");
        assertThat(metadata.subtitle()).isEqualTo("There and Back Again");
        assertThat(metadata.description()).isEqualTo("A fantasy novel");
        assertThat(metadata.language()).isEqualTo("en");
        assertThat(metadata.publisher()).isEqualTo("Allen & Unwin");
        assertThat(metadata.published()).isEqualTo("1937-09-21");
        assertThat(metadata.author()).extracting("name").containsExactly("J.R.R. Tolkien");
        assertThat(metadata.subject()).containsExactly("Fantasy");
        assertThat(metadata.identifier()).isEqualTo("urn:isbn:9780261102217");
        assertThat(metadata.modified()).isEqualTo("2025-01-01T12:00:00Z");
    }

    @Test
    void toMetadata_prefersIsbn13ThenFallsBackToIsbn10() {
        Book only10 = Book.builder()
                .metadata(BookMetadata.builder().title("X").isbn10("0261102214").build())
                .build();

        assertThat(mapper.toMetadata(only10).identifier()).isEqualTo("urn:isbn:0261102214");
    }

    @Test
    void toMetadata_mapsSeriesToBelongsTo() {
        Book book = Book.builder()
                .metadata(BookMetadata.builder()
                        .title("The Two Towers")
                        .seriesName("The Lord of the Rings")
                        .seriesNumber(2f)
                        .build())
                .build();

        OpdsPublicationMetadata metadata = mapper.toMetadata(book);

        assertThat(metadata.belongsTo()).isNotNull();
        assertThat(metadata.belongsTo().series()).hasSize(1);
        assertThat(metadata.belongsTo().series().getFirst().name()).isEqualTo("The Lord of the Rings");
        assertThat(metadata.belongsTo().series().getFirst().position()).isEqualTo(2f);
    }

    @Test
    void toMetadata_sortsSubjectsForDeterministicOutput() {
        Book book = Book.builder()
                .metadata(BookMetadata.builder()
                        .title("X")
                        .categories(new java.util.LinkedHashSet<>(List.of("Zebra", "Adventure", "Mystery")))
                        .build())
                .build();

        assertThat(mapper.toMetadata(book).subject()).containsExactly("Adventure", "Mystery", "Zebra");
    }

    @Test
    void toMetadata_omitsAbsentOptionalFields() {
        Book book = Book.builder()
                .metadata(BookMetadata.builder().title("Bare").build())
                .build();

        OpdsPublicationMetadata metadata = mapper.toMetadata(book);

        assertThat(metadata.identifier()).isNull();
        assertThat(metadata.belongsTo()).isNull();
        assertThat(metadata.author()).isNull();
        assertThat(metadata.subject()).isNull();
    }
}
