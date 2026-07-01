package org.booklore.model.dto.opds;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Feed-level metadata for an OPDS 2.0 catalog feed. Pagination counters are
 * omitted when null (e.g. for pure navigation feeds).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsFeedMetadata(
        String title,
        Integer numberOfItems,
        Integer itemsPerPage,
        Integer currentPage
) {
    public static OpdsFeedMetadata title(String title) {
        return new OpdsFeedMetadata(title, null, null, null);
    }
}
