package org.booklore.model.dto.opds;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A series a publication belongs to, referenced from {@link OpdsBelongsTo}.
 * {@code position} maps to the book's series number.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsSeries(String name, Float position) {
}
