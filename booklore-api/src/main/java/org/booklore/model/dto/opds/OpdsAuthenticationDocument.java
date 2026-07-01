package org.booklore.model.dto.opds;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * OPDS 2.0 Authentication Document (media type {@code application/opds-authentication+json}).
 * Served from {@code /api/v2/opds/auth} and advertised via a {@code Link} header on 401 responses.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OpdsAuthenticationDocument(
        String id,
        String title,
        String description,
        List<OpdsAuthenticationFlow> authentication,
        List<OpdsLink> links
) {
}
