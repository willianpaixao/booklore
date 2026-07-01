package org.booklore.service.opds;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.booklore.model.dto.opds.OpdsBelongsTo;
import org.booklore.model.dto.opds.OpdsContributor;
import org.booklore.model.dto.opds.OpdsFeed;
import org.booklore.model.dto.opds.OpdsFeedMetadata;
import org.booklore.model.dto.opds.OpdsLink;
import org.booklore.model.dto.opds.OpdsPublication;
import org.booklore.model.dto.opds.OpdsPublicationMetadata;
import org.booklore.model.dto.opds.OpdsSeries;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the on-the-wire JSON shape of OPDS 2.0 DTOs (schema.org {@code @type} renaming,
 * {@code templated} omission, and null/empty pruning) matches the OPDS 2.0 contract.
 */
class OpdsJsonSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void feed_serializesWithExpectedShape() throws Exception {
        OpdsPublicationMetadata pubMeta = new OpdsPublicationMetadata(
                "http://schema.org/Book",
                "Dune",
                null,
                "urn:isbn:9780441013593",
                List.of(new OpdsContributor("Frank Herbert")),
                "en",
                null,
                "2025-01-01T12:00:00Z",
                null,
                null,
                List.of("Science Fiction"),
                new OpdsBelongsTo(List.of(new OpdsSeries("Dune", 1f)))
        );
        OpdsPublication publication = new OpdsPublication(
                pubMeta,
                List.of(new OpdsLink("http://opds-spec.org/acquisition", "/api/v2/opds/1/download?fileId=2", "application/epub+zip", "EPUB", null, null)),
                List.of(new OpdsLink("http://opds-spec.org/image", "/api/v2/opds/1/cover", "image/jpeg", null, null, null))
        );
        OpdsFeed feed = new OpdsFeed(
                new OpdsFeedMetadata("Booklore Catalog", 1, 50, 1),
                List.of(
                        OpdsLink.of("self", "/api/v2/opds/catalog", "application/opds+json"),
                        OpdsLink.templated("search", "/api/v2/opds/catalog{?query}", "application/opds+json")
                ),
                null,
                List.of(publication)
        );

        String json = mapper.writeValueAsString(feed);

        // schema.org @type is renamed via @JsonProperty
        assertThat(json).contains("\"@type\":\"http://schema.org/Book\"");
        // templated only present (and true) on the search link
        assertThat(json).contains("\"templated\":true");
        // null feed-level navigation is pruned entirely
        assertThat(json).doesNotContain("navigation");
        // null publication metadata fields are pruned
        assertThat(json).doesNotContain("subtitle");
        assertThat(json).doesNotContain("publisher");
        assertThat(json).contains("\"numberOfItems\":1");
        assertThat(json).contains("\"belongsTo\"");

        // round-trips back to an equal object
        OpdsFeed roundTrip = mapper.readValue(json, OpdsFeed.class);
        assertThat(roundTrip.metadata().title()).isEqualTo("Booklore Catalog");
        assertThat(roundTrip.publications().getFirst().metadata().type()).isEqualTo("http://schema.org/Book");
    }

    @Test
    void nonTemplatedLink_omitsTemplatedField() throws Exception {
        String json = mapper.writeValueAsString(OpdsLink.of("self", "/api/v2/opds", "application/opds+json"));
        assertThat(json).doesNotContain("templated");
        assertThat(json).doesNotContain("properties");
        assertThat(json).doesNotContain("title");
    }

    @Test
    void explicitFalseTemplated_isNormalizedAndNotSerialized() throws Exception {
        OpdsLink link = new OpdsLink("self", "/api/v2/opds", "application/opds+json", null, false, null);
        assertThat(link.templated()).isNull();
        assertThat(mapper.writeValueAsString(link)).doesNotContain("templated");
    }
}
