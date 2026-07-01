package org.booklore.model.dto.opds;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * schema.org publication metadata for an OPDS 2.0 publication. The {@code @type}
 * field is emitted via {@link JsonProperty} since it is not a valid Java identifier.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsPublicationMetadata(
        @JsonProperty("@type") String type,
        String title,
        String subtitle,
        String identifier,
        List<OpdsContributor> author,
        String language,
        String publisher,
        String modified,
        String published,
        String description,
        List<String> subject,
        OpdsBelongsTo belongsTo
) {
}
