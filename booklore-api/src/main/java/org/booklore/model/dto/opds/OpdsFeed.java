package org.booklore.model.dto.opds;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Top-level OPDS 2.0 catalog feed. A feed carries {@code metadata} and {@code links},
 * plus any combination of {@code navigation} (a compact collection of links) and
 * {@code publications}. Empty collections are omitted from the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OpdsFeed(
        OpdsFeedMetadata metadata,
        List<OpdsLink> links,
        List<OpdsLink> navigation,
        List<OpdsPublication> publications
) {
}
