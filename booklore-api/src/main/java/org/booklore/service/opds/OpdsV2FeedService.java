package org.booklore.service.opds;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.config.security.userdetails.OpdsUserDetails;
import org.booklore.exception.ApiError;
import org.booklore.mapper.OpdsPublicationMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookFile;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.opds.OpdsAuthenticationDocument;
import org.booklore.model.dto.opds.OpdsAuthenticationFlow;
import org.booklore.model.dto.opds.OpdsFeed;
import org.booklore.model.dto.opds.OpdsFeedMetadata;
import org.booklore.model.dto.opds.OpdsLink;
import org.booklore.model.dto.opds.OpdsPublication;
import org.booklore.model.dto.opds.OpdsPublicationMetadata;
import org.booklore.model.enums.OpdsSortOrder;
import org.booklore.service.MagicShelfService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders OPDS 2.0 (JSON) feeds. Mirrors the feed surface of the OPDS 1.2
 * {@link OpdsFeedService} but returns typed DTOs (serialized by Jackson) instead of XML.
 * All book data, filtering, access control and sorting are reused from
 * {@link OpdsBookService} / {@link MagicShelfBookService} — only the rendering differs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpdsV2FeedService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private static final String BASE = "/api/v2/opds";
    public static final String FEED_MEDIA_TYPE = "application/opds+json";
    public static final String AUTH_MEDIA_TYPE = "application/opds-authentication+json";

    private static final String REL_SELF = "self";
    private static final String REL_START = "start";
    private static final String REL_SEARCH = "search";
    private static final String REL_SUBSECTION = "subsection";
    private static final String REL_ACQUISITION = "http://opds-spec.org/acquisition";
    private static final String REL_IMAGE = "http://opds-spec.org/image";
    private static final String REL_THUMBNAIL = "http://opds-spec.org/image/thumbnail";

    private final AuthenticationService authenticationService;
    private final OpdsBookService opdsBookService;
    private final MagicShelfService magicShelfService;
    private final MagicShelfBookService magicShelfBookService;
    private final OpdsPublicationMapper opdsPublicationMapper;

    public OpdsFeed generateRootNavigation() {
        List<OpdsLink> navigation = new ArrayList<>();
        navigation.add(navLink("All Books", BASE + "/catalog?page=1&size=" + DEFAULT_PAGE_SIZE));
        navigation.add(navLink("Recently Added", BASE + "/recent?page=1&size=" + DEFAULT_PAGE_SIZE));
        navigation.add(navLink("Libraries", BASE + "/libraries"));
        navigation.add(navLink("Shelves", BASE + "/shelves"));
        navigation.add(navLink("Magic Shelves", BASE + "/magic-shelves"));
        navigation.add(navLink("Authors", BASE + "/authors"));
        navigation.add(navLink("Series", BASE + "/series"));
        navigation.add(navLink("Surprise Me", BASE + "/surprise"));

        return new OpdsFeed(
                OpdsFeedMetadata.title("Booklore Catalog"),
                navigationFeedLinks(BASE),
                navigation,
                null
        );
    }

    public OpdsFeed generateLibrariesNavigation() {
        Long userId = getUserId();
        List<Library> libraries = opdsBookService.getAccessibleLibraries(userId);

        List<OpdsLink> navigation = libraries.stream()
                .map(lib -> navLink(lib.getName(), BASE + "/catalog?libraryId=" + lib.getId()))
                .toList();

        return new OpdsFeed(OpdsFeedMetadata.title("Libraries"), navigationFeedLinks(BASE + "/libraries"), navigation, null);
    }

    public OpdsFeed generateShelvesNavigation() {
        Long userId = getUserId();

        List<OpdsLink> navigation = new ArrayList<>();
        if (userId != null) {
            var shelves = opdsBookService.getUserShelves(userId);
            if (shelves != null) {
                shelves.forEach(shelf ->
                        navigation.add(navLink(shelf.getName(), BASE + "/catalog?shelfId=" + shelf.getId())));
            }
        }

        return new OpdsFeed(OpdsFeedMetadata.title("Shelves"), navigationFeedLinks(BASE + "/shelves"), navigation, null);
    }

    public OpdsFeed generateMagicShelvesNavigation() {
        Long userId = getUserId();

        List<OpdsLink> navigation = new ArrayList<>();
        if (userId != null) {
            var magicShelves = magicShelfService.getUserShelvesForOpds(userId);
            if (magicShelves != null) {
                magicShelves.forEach(shelf ->
                        navigation.add(navLink(shelf.getName(), BASE + "/catalog?magicShelfId=" + shelf.getId())));
            }
        }

        return new OpdsFeed(OpdsFeedMetadata.title("Magic Shelves"), navigationFeedLinks(BASE + "/magic-shelves"), navigation, null);
    }

    public OpdsFeed generateAuthorsNavigation() {
        Long userId = getUserId();
        List<String> authors = opdsBookService.getDistinctAuthors(userId);

        List<OpdsLink> navigation = authors.stream()
                .map(author -> navLink(author, BASE + "/catalog?author=" + encode(author)))
                .toList();

        return new OpdsFeed(OpdsFeedMetadata.title("Authors"), navigationFeedLinks(BASE + "/authors"), navigation, null);
    }

    public OpdsFeed generateSeriesNavigation() {
        Long userId = getUserId();
        List<String> seriesList = opdsBookService.getDistinctSeries(userId);

        List<OpdsLink> navigation = seriesList.stream()
                .map(series -> navLink(series, BASE + "/catalog?series=" + encode(series)))
                .toList();

        return new OpdsFeed(OpdsFeedMetadata.title("Series"), navigationFeedLinks(BASE + "/series"), navigation, null);
    }

    public OpdsFeed generateCatalogFeed(HttpServletRequest request) {
        Long libraryId = parseLongParam(request, "libraryId", null);
        Set<Long> shelfIds = parseShelfIds(request);
        Long magicShelfId = parseLongParam(request, "magicShelfId", null);
        String query = queryParam(request);
        String author = request.getParameter("author");
        String series = request.getParameter("series");
        int page = parsePage(request);
        int size = parseSize(request);

        Long userId = getUserId();
        OpdsSortOrder sortOrder = getSortOrder();
        Page<Book> booksPage;

        if (magicShelfId != null) {
            booksPage = magicShelfBookService.getBooksByMagicShelfId(userId, magicShelfId, page - 1, size);
        } else if (author != null && !author.isBlank()) {
            booksPage = opdsBookService.getBooksByAuthorName(userId, author, page - 1, size);
        } else if (series != null && !series.isBlank()) {
            booksPage = opdsBookService.getBooksBySeriesName(userId, series, page - 1, size);
        } else {
            booksPage = opdsBookService.getBooksPage(userId, query, libraryId, shelfIds, page - 1, size);
        }

        booksPage = opdsBookService.applySortOrder(booksPage, sortOrder);

        String feedTitle = determineFeedTitle(libraryId, shelfIds, magicShelfId, author, series);
        return acquisitionFeed(feedTitle, request, booksPage, page, size);
    }

    public OpdsFeed generateRecentFeed(HttpServletRequest request) {
        Long userId = getUserId();
        OpdsSortOrder sortOrder = getSortOrder();
        int page = parsePage(request);
        int size = parseSize(request);

        Page<Book> booksPage = opdsBookService.getRecentBooksPage(userId, page - 1, size);
        booksPage = opdsBookService.applySortOrder(booksPage, sortOrder);

        return acquisitionFeed("Recently Added Books", request, booksPage, page, size);
    }

    public OpdsFeed generateSurpriseFeed() {
        Long userId = getUserId();
        int count = 25;
        List<Book> books = opdsBookService.getRandomBooks(userId, count);

        List<OpdsPublication> publications = books.stream().map(this::toPublication).toList();

        OpdsFeedMetadata metadata = new OpdsFeedMetadata("Surprise Me", books.size(), count, 1);
        List<OpdsLink> links = new ArrayList<>();
        links.add(OpdsLink.of(REL_SELF, BASE + "/surprise", FEED_MEDIA_TYPE));
        links.add(OpdsLink.of(REL_START, BASE, FEED_MEDIA_TYPE));
        links.add(searchLink());

        return new OpdsFeed(metadata, links, null, publications);
    }

    public OpdsAuthenticationDocument generateAuthenticationDocument() {
        OpdsAuthenticationFlow basic = new OpdsAuthenticationFlow(
                "http://opds-spec.org/auth/basic",
                List.of(new OpdsLink("authenticate", BASE, FEED_MEDIA_TYPE, null, null, null))
        );
        return new OpdsAuthenticationDocument(
                BASE + "/auth",
                "Booklore OPDS",
                "Authenticate with your Booklore OPDS credentials.",
                List.of(basic),
                List.of(OpdsLink.of(REL_START, BASE, FEED_MEDIA_TYPE))
        );
    }

    private OpdsFeed acquisitionFeed(String title, HttpServletRequest request, Page<Book> booksPage, int page, int size) {
        List<OpdsPublication> publications = booksPage.getContent().stream()
                .map(this::toPublication)
                .toList();

        OpdsFeedMetadata metadata = new OpdsFeedMetadata(
                title,
                (int) booksPage.getTotalElements(),
                size,
                page
        );

        List<OpdsLink> links = new ArrayList<>();
        links.add(OpdsLink.of(REL_SELF, buildPaginationUrl(request, page, size), FEED_MEDIA_TYPE));
        links.add(OpdsLink.of(REL_START, BASE, FEED_MEDIA_TYPE));
        links.add(searchLink());
        appendPaginationLinks(links, request, page, booksPage.getTotalPages(), size);

        return new OpdsFeed(metadata, links, null, publications);
    }

    private OpdsPublication toPublication(Book book) {
        OpdsPublicationMetadata metadata = opdsPublicationMapper.toMetadata(book);

        List<OpdsLink> links = new ArrayList<>();
        if (book.getPrimaryFile() != null) {
            links.add(acquisitionLink(book.getId(), book.getPrimaryFile()));
        }
        if (book.getAlternativeFormats() != null) {
            for (BookFile altFormat : book.getAlternativeFormats()) {
                OpdsLink link = acquisitionLink(book.getId(), altFormat);
                if (link != null) {
                    links.add(link);
                }
            }
        }
        links.removeIf(java.util.Objects::isNull);

        List<OpdsLink> images = new ArrayList<>();
        if (book.getMetadata() != null && book.getMetadata().getCoverUpdatedOn() != null) {
            String coverUrl = BASE + "/" + book.getId() + "/cover?" + book.getMetadata().getCoverUpdatedOn();
            images.add(new OpdsLink(REL_IMAGE, coverUrl, "image/jpeg", null, null, null));
            images.add(new OpdsLink(REL_THUMBNAIL, coverUrl, "image/jpeg", null, null, null));
        }

        return new OpdsPublication(metadata, links, images);
    }

    private OpdsLink acquisitionLink(Long bookId, BookFile bookFile) {
        if (bookFile == null || bookFile.getId() == null) {
            return null;
        }
        String href = BASE + "/" + bookId + "/download?fileId=" + bookFile.getId();
        String type = OpdsMimeTypeResolver.resolve(bookFile);
        String title = bookFile.getBookType() != null ? bookFile.getBookType().name() : null;
        return new OpdsLink(REL_ACQUISITION, href, type, title, null, null);
    }

    private void appendPaginationLinks(List<OpdsLink> links, HttpServletRequest request, int currentPage, int totalPages, int size) {
        if (totalPages > 0) {
            links.add(OpdsLink.of("first", buildPaginationUrl(request, 1, size), FEED_MEDIA_TYPE));
        }
        if (currentPage > 1) {
            links.add(OpdsLink.of("previous", buildPaginationUrl(request, currentPage - 1, size), FEED_MEDIA_TYPE));
        }
        if (currentPage < totalPages) {
            links.add(OpdsLink.of("next", buildPaginationUrl(request, currentPage + 1, size), FEED_MEDIA_TYPE));
        }
        if (totalPages > 0) {
            links.add(OpdsLink.of("last", buildPaginationUrl(request, totalPages, size), FEED_MEDIA_TYPE));
        }
    }

    private List<OpdsLink> navigationFeedLinks(String selfHref) {
        List<OpdsLink> links = new ArrayList<>();
        links.add(OpdsLink.of(REL_SELF, selfHref, FEED_MEDIA_TYPE));
        links.add(OpdsLink.of(REL_START, BASE, FEED_MEDIA_TYPE));
        links.add(searchLink());
        return links;
    }

    private OpdsLink navLink(String title, String href) {
        return new OpdsLink(REL_SUBSECTION, href, FEED_MEDIA_TYPE, title, null, null);
    }

    private OpdsLink searchLink() {
        return OpdsLink.templated(REL_SEARCH, BASE + "/catalog{?query}", FEED_MEDIA_TYPE);
    }

    private String buildPaginationUrl(HttpServletRequest request, int page, int size) {
        String url = request.getRequestURI();
        StringBuilder result = new StringBuilder(url).append("?");

        String queryString = request.getQueryString();
        if (queryString != null) {
            Arrays.stream(queryString.split("&"))
                    .filter(param -> !param.startsWith("page=") && !param.startsWith("size="))
                    .forEach(param -> result.append(param).append("&"));
        }

        result.append("page=").append(page).append("&size=").append(size);
        return result.toString();
    }

    private String determineFeedTitle(Long libraryId, Set<Long> shelfIds, Long magicShelfId, String author, String series) {
        if (magicShelfId != null) {
            return magicShelfBookService.getMagicShelfName(magicShelfId);
        }
        if (shelfIds != null && !shelfIds.isEmpty()) {
            if (shelfIds.size() == 1) {
                return opdsBookService.getShelfName(shelfIds.iterator().next());
            }
            return "Multiple Shelves";
        }
        if (libraryId != null) {
            return opdsBookService.getLibraryName(libraryId);
        }
        if (author != null && !author.isBlank()) {
            return "Books by " + author;
        }
        if (series != null && !series.isBlank()) {
            return series + " series";
        }
        return "Booklore Catalog";
    }

    private String queryParam(HttpServletRequest request) {
        String query = request.getParameter("q");
        if (query == null || query.isBlank()) {
            query = request.getParameter("query");
        }
        return query;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private int parsePage(HttpServletRequest request) {
        // Clamp as a long before narrowing so out-of-range values can't overflow to a negative page.
        long page = Math.max(1L, parseLongParam(request, "page", 1L));
        return (int) Math.min(page, Integer.MAX_VALUE);
    }

    private int parseSize(HttpServletRequest request) {
        long size = Math.min(parseLongParam(request, "size", (long) DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE);
        return (int) Math.max(1L, size);
    }

    private Long parseLongParam(HttpServletRequest request, String name, Long defaultValue) {
        try {
            String v = request.getParameter(name);
            if (v == null || v.isBlank()) return defaultValue;
            return Long.parseLong(v);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Set<Long> parseShelfIds(HttpServletRequest request) {
        String shelfIdParam = request.getParameter("shelfId");
        String shelfIdsParam = request.getParameter("shelfIds");

        Set<Long> shelfIds = new HashSet<>();

        if (shelfIdParam != null && !shelfIdParam.isBlank()) {
            try {
                shelfIds.add(Long.parseLong(shelfIdParam));
            } catch (NumberFormatException e) {
                log.warn("Invalid shelfId parameter: {}", shelfIdParam);
            }
        }

        if (shelfIdsParam != null && !shelfIdsParam.isBlank()) {
            for (String id : shelfIdsParam.split(",")) {
                try {
                    shelfIds.add(Long.parseLong(id.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid shelf ID in shelfIds parameter: {}", id);
                }
            }
        }

        return shelfIds.isEmpty() ? null : shelfIds;
    }

    private Long getUserId() {
        OpdsUserDetails details = authenticationService.getOpdsUser();
        if (details == null || details.getOpdsUserV2() == null) {
            throw ApiError.FORBIDDEN.createException("OPDS authentication required");
        }
        return details.getOpdsUserV2().getUserId();
    }

    private OpdsSortOrder getSortOrder() {
        OpdsUserDetails details = authenticationService.getOpdsUser();
        return details != null && details.getOpdsUserV2() != null && details.getOpdsUserV2().getSortOrder() != null
                ? details.getOpdsUserV2().getSortOrder()
                : OpdsSortOrder.RECENT;
    }
}
