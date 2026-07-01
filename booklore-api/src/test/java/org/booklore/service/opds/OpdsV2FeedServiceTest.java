package org.booklore.service.opds;

import jakarta.servlet.http.HttpServletRequest;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.config.security.userdetails.OpdsUserDetails;
import org.booklore.mapper.OpdsPublicationMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookFile;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.OpdsUserV2;
import org.booklore.model.dto.opds.OpdsAuthenticationDocument;
import org.booklore.model.dto.opds.OpdsFeed;
import org.booklore.model.dto.opds.OpdsLink;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.OpdsSortOrder;
import org.booklore.service.MagicShelfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpdsV2FeedServiceTest {

    private static final Long TEST_USER_ID = 42L;

    private AuthenticationService authenticationService;
    private OpdsBookService opdsBookService;
    private MagicShelfService magicShelfService;
    private MagicShelfBookService magicShelfBookService;
    private OpdsV2FeedService service;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        authenticationService = mock(AuthenticationService.class);
        opdsBookService = mock(OpdsBookService.class);
        magicShelfService = mock(MagicShelfService.class);
        magicShelfBookService = mock(MagicShelfBookService.class);
        OpdsPublicationMapper mapper = Mappers.getMapper(OpdsPublicationMapper.class);
        service = new OpdsV2FeedService(authenticationService, opdsBookService, magicShelfService, magicShelfBookService, mapper);
        request = mock(HttpServletRequest.class);
    }

    private void mockAuthenticatedUser() {
        OpdsUserDetails userDetails = mock(OpdsUserDetails.class);
        OpdsUserV2 v2 = mock(OpdsUserV2.class);
        when(userDetails.getOpdsUserV2()).thenReturn(v2);
        when(v2.getUserId()).thenReturn(TEST_USER_ID);
        when(v2.getSortOrder()).thenReturn(OpdsSortOrder.RECENT);
        when(authenticationService.getOpdsUser()).thenReturn(userDetails);
    }

    @Test
    void generateRootNavigation_containsAllSectionsAndSelfLink() {
        OpdsFeed feed = service.generateRootNavigation();

        assertThat(feed.metadata().title()).isEqualTo("Booklore Catalog");
        assertThat(feed.navigation()).extracting(OpdsLink::title)
                .contains("All Books", "Recently Added", "Libraries", "Shelves", "Magic Shelves", "Authors", "Series", "Surprise Me");
        assertThat(feed.navigation()).allSatisfy(link ->
                assertThat(link.type()).isEqualTo(OpdsV2FeedService.FEED_MEDIA_TYPE));
        assertThat(feed.links()).anySatisfy(link -> {
            assertThat(link.rel()).isEqualTo("self");
            assertThat(link.href()).isEqualTo("/api/v2/opds");
        });
        assertThat(feed.links()).anySatisfy(link -> {
            assertThat(link.rel()).isEqualTo("search");
            assertThat(link.templated()).isTrue();
        });
        assertThat(feed.publications()).isNull();
    }

    @Test
    void generateLibrariesNavigation_listsAccessibleLibraries() {
        mockAuthenticatedUser();
        Library lib = Library.builder().id(3L).name("Sci-Fi").watch(false).build();
        when(opdsBookService.getAccessibleLibraries(TEST_USER_ID)).thenReturn(List.of(lib));

        OpdsFeed feed = service.generateLibrariesNavigation();

        assertThat(feed.navigation()).hasSize(1);
        assertThat(feed.navigation().getFirst().title()).isEqualTo("Sci-Fi");
        assertThat(feed.navigation().getFirst().href()).isEqualTo("/api/v2/opds/catalog?libraryId=3");
    }

    @Test
    void generateCatalogFeed_rendersPublicationsWithPaginationAndAcquisition() {
        mockAuthenticatedUser();
        when(request.getRequestURI()).thenReturn("/api/v2/opds/catalog");
        when(request.getQueryString()).thenReturn(null);

        Book book = Book.builder()
                .id(11L)
                .addedOn(Instant.parse("2025-01-01T12:00:00Z"))
                .metadata(BookMetadata.builder()
                        .title("Dune")
                        .coverUpdatedOn(Instant.parse("2025-01-01T12:00:00Z"))
                        .build())
                .primaryFile(BookFile.builder().id(99L).bookType(BookFileType.EPUB).fileName("dune.epub").build())
                .build();
        Page<Book> page = new PageImpl<>(List.of(book), PageRequest.of(0, 50), 1);
        when(opdsBookService.getBooksPage(eq(TEST_USER_ID), any(), any(), any(), eq(0), eq(50))).thenReturn(page);
        when(opdsBookService.applySortOrder(any(), any())).thenReturn(page);

        OpdsFeed feed = service.generateCatalogFeed(request);

        assertThat(feed.metadata().numberOfItems()).isEqualTo(1);
        assertThat(feed.metadata().itemsPerPage()).isEqualTo(50);
        assertThat(feed.metadata().currentPage()).isEqualTo(1);
        assertThat(feed.publications()).hasSize(1);

        var publication = feed.publications().getFirst();
        assertThat(publication.metadata().title()).isEqualTo("Dune");
        assertThat(publication.links()).anySatisfy(link -> {
            assertThat(link.rel()).isEqualTo("http://opds-spec.org/acquisition");
            assertThat(link.type()).isEqualTo("application/epub+zip");
            assertThat(link.href()).isEqualTo("/api/v2/opds/11/download?fileId=99");
        });
        assertThat(publication.images()).isNotEmpty();
        assertThat(feed.links()).extracting(OpdsLink::rel).contains("self", "first", "last");
    }

    @Test
    void generateCatalogFeed_clampsOversizedPageSizeWithoutOverflow() {
        mockAuthenticatedUser();
        when(request.getRequestURI()).thenReturn("/api/v2/opds/catalog");
        when(request.getQueryString()).thenReturn(null);
        when(request.getParameter("size")).thenReturn("2147483648"); // Integer.MAX_VALUE + 1

        Page<Book> empty = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        // Stubbed with size=100 proves the oversized value was clamped as a long, not overflowed to a negative int.
        when(opdsBookService.getBooksPage(eq(TEST_USER_ID), any(), any(), any(), eq(0), eq(100))).thenReturn(empty);
        when(opdsBookService.applySortOrder(any(), any())).thenReturn(empty);

        OpdsFeed feed = service.generateCatalogFeed(request);

        assertThat(feed.metadata().itemsPerPage()).isEqualTo(100);
        assertThat(feed.metadata().currentPage()).isEqualTo(1);
    }

    @Test
    void generateAuthenticationDocument_advertisesBasicAuth() {
        OpdsAuthenticationDocument doc = service.generateAuthenticationDocument();

        assertThat(doc.title()).isEqualTo("Booklore OPDS");
        assertThat(doc.authentication()).hasSize(1);
        assertThat(doc.authentication().getFirst().type()).isEqualTo("http://opds-spec.org/auth/basic");
    }
}
