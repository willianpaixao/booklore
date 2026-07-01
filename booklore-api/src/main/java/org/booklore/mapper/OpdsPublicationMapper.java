package org.booklore.mapper;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.opds.OpdsBelongsTo;
import org.booklore.model.dto.opds.OpdsContributor;
import org.booklore.model.dto.opds.OpdsPublicationMetadata;
import org.booklore.model.dto.opds.OpdsSeries;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Maps a {@link Book} DTO to the schema.org {@link OpdsPublicationMetadata} used in OPDS 2.0 feeds.
 * Acquisition links and cover images are assembled in {@code OpdsV2FeedService} since they require
 * request-scoped data (book/file ids, MIME types, base path).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OpdsPublicationMapper {

    @Mapping(target = "type", constant = "http://schema.org/Book")
    @Mapping(target = "title", source = "metadata.title")
    @Mapping(target = "subtitle", source = "metadata.subtitle")
    @Mapping(target = "language", source = "metadata.language")
    @Mapping(target = "publisher", source = "metadata.publisher")
    @Mapping(target = "description", source = "metadata.description")
    @Mapping(target = "author", source = "metadata.authors", qualifiedByName = "toContributors")
    @Mapping(target = "subject", source = "metadata.categories", qualifiedByName = "toSubjects")
    @Mapping(target = "identifier", source = "metadata", qualifiedByName = "toIdentifier")
    @Mapping(target = "belongsTo", source = "metadata", qualifiedByName = "toBelongsTo")
    @Mapping(target = "published", source = "metadata.publishedDate", qualifiedByName = "toPublished")
    @Mapping(target = "modified", expression = "java(toModified(book))")
    OpdsPublicationMetadata toMetadata(Book book);

    @Named("toContributors")
    default List<OpdsContributor> toContributors(List<String> authors) {
        if (authors == null || authors.isEmpty()) {
            return null;
        }
        List<OpdsContributor> contributors = authors.stream()
                .filter(a -> a != null && !a.isBlank())
                .map(OpdsContributor::new)
                .toList();
        return contributors.isEmpty() ? null : contributors;
    }

    @Named("toSubjects")
    default List<String> toSubjects(Set<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        List<String> subjects = categories.stream()
                .filter(c -> c != null && !c.isBlank())
                .sorted()
                .toList();
        return subjects.isEmpty() ? null : subjects;
    }

    @Named("toIdentifier")
    default String toIdentifier(BookMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        String isbn = metadata.getIsbn13() != null && !metadata.getIsbn13().isBlank()
                ? metadata.getIsbn13()
                : metadata.getIsbn10();
        return isbn != null && !isbn.isBlank() ? "urn:isbn:" + isbn : null;
    }

    @Named("toBelongsTo")
    default OpdsBelongsTo toBelongsTo(BookMetadata metadata) {
        if (metadata == null || metadata.getSeriesName() == null || metadata.getSeriesName().isBlank()) {
            return null;
        }
        return new OpdsBelongsTo(List.of(new OpdsSeries(metadata.getSeriesName(), metadata.getSeriesNumber())));
    }

    @Named("toPublished")
    default String toPublished(LocalDate publishedDate) {
        return publishedDate != null ? publishedDate.toString() : null;
    }

    default String toModified(Book book) {
        if (book == null) {
            return null;
        }
        Instant instant = book.getMetadata() != null && book.getMetadata().getCoverUpdatedOn() != null
                ? book.getMetadata().getCoverUpdatedOn()
                : book.getAddedOn();
        return instant != null ? DateTimeFormatter.ISO_INSTANT.format(instant) : null;
    }
}
