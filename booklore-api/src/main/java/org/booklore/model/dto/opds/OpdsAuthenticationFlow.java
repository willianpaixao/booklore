package org.booklore.model.dto.opds;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A single supported authentication flow within an {@link OpdsAuthenticationDocument}.
 * {@code type} identifies the mechanism, e.g. {@code http://opds-spec.org/auth/basic}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OpdsAuthenticationFlow(
        String type,
        List<OpdsLink> links
) {
}
