package org.booklore.model.dto.opds;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * A link object as defined by the OPDS 2.0 / Readium Web Publication Manifest model.
 * {@code templated} is only serialized when explicitly {@code true}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsLink(
        String rel,
        String href,
        String type,
        String title,
        Boolean templated,
        Map<String, Object> properties
) {
    public OpdsLink {
        // Normalize false -> null so a non-templated link never serializes "templated":false.
        if (Boolean.FALSE.equals(templated)) {
            templated = null;
        }
    }

    public static OpdsLink of(String rel, String href, String type) {
        return new OpdsLink(rel, href, type, null, null, null);
    }

    public static OpdsLink templated(String rel, String href, String type) {
        return new OpdsLink(rel, href, type, null, Boolean.TRUE, null);
    }
}
