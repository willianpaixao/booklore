package org.booklore.model.dto.opds;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A single publication within an OPDS 2.0 feed: schema.org metadata plus
 * acquisition {@code links} and cover {@code images}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OpdsPublication(
        OpdsPublicationMetadata metadata,
        List<OpdsLink> links,
        List<OpdsLink> images
) {
}
