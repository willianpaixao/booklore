package org.booklore.model.dto.opds;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A schema.org contributor (author, translator, ...) represented as a name object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsContributor(String name) {
}
