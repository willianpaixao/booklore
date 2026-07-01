package org.booklore.model.dto.opds;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The {@code belongsTo} metadata element linking a publication to the collection(s)
 * or series it is part of.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OpdsBelongsTo(List<OpdsSeries> series) {
}
