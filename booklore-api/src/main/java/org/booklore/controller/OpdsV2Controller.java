package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.config.security.userdetails.OpdsUserDetails;
import org.booklore.model.dto.opds.OpdsAuthenticationDocument;
import org.booklore.model.dto.opds.OpdsFeed;
import org.booklore.service.book.BookDownloadService;
import org.booklore.service.book.BookService;
import org.booklore.service.opds.OpdsBookService;
import org.booklore.service.opds.OpdsV2FeedService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPDS 2.0 (JSON) catalog endpoints, served alongside the OPDS 1.2 (XML) {@link OpdsController}.
 * Shares the same security chain, enable/disable interceptor, credentials and permissions.
 */
@Tag(name = "OPDS 2.0", description = "OPDS 2.0 JSON catalog feeds, book downloads, covers, and authentication document")
@Slf4j
@RestController
@RequestMapping("/api/v2/opds")
@RequiredArgsConstructor
public class OpdsV2Controller {

    private static final String FEED_MEDIA_TYPE = OpdsV2FeedService.FEED_MEDIA_TYPE + ";charset=utf-8";
    private static final String AUTH_MEDIA_TYPE = OpdsV2FeedService.AUTH_MEDIA_TYPE + ";charset=utf-8";

    private final OpdsV2FeedService opdsV2FeedService;
    private final OpdsBookService opdsBookService;
    private final BookService bookService;
    private final BookDownloadService bookDownloadService;
    private final AuthenticationService authenticationService;

    @Operation(summary = "Get OPDS 2.0 root catalog", description = "Retrieve the OPDS 2.0 root navigation feed.")
    @ApiResponse(responseCode = "200", description = "Root feed returned successfully")
    @GetMapping(produces = FEED_MEDIA_TYPE)
    public ResponseEntity<OpdsFeed> getRoot() {
        return feed(opdsV2FeedService.generateRootNavigation());
    }

    @Operation(summary = "Get OPDS 2.0 libraries navigation")
    @ApiResponse(responseCode = "200", description = "Libraries feed returned successfully")
    @GetMapping(value = "/libraries", produces = FEED_MEDIA_TYPE)
    public ResponseEntity<OpdsFeed> getLibraries() {
        return feed(opdsV2FeedService.generateLibrariesNavigation());
    }

    @Operation(summary = "Get OPDS 2.0 shelves navigation")
    @ApiResponse(responseCode = "200", description = "Shelves feed returned successfully")
    @GetMapping(value = "/shelves", produces = FEED_MEDIA_TYPE)
    public ResponseEntity<OpdsFeed> getShelves() {
        return feed(opdsV2FeedService.generateShelvesNavigation());
    }

    @Operation(summary = "Get OPDS 2.0 magic shelves navigation")
    @ApiResponse(responseCode = "200", description = "Magic shelves feed returned successfully")
    @GetMapping(value = "/magic-shelves", produces = FEED_MEDIA_TYPE)
    public ResponseEntity<OpdsFeed> getMagicShelves() {
        return feed(opdsV2FeedService.generateMagicShelvesNavigation());
    }

    @Operation(summary = "Get OPDS 2.0 authors navigation")
    @ApiResponse(responseCode = "200", description = "Authors feed returned successfully")
    @GetMapping(value = "/authors", produces = FEED_MEDIA_TYPE)
    public ResponseEntity<OpdsFeed> getAuthors() {
        return feed(opdsV2FeedService.generateAuthorsNavigation());
    }

    @Operation(summary = "Get OPDS 2.0 series navigation")
    @ApiResponse(responseCode = "200", description = "Series feed returned successfully")
    @GetMapping(value = "/series", produces = FEED_MEDIA_TYPE)
    public ResponseEntity<OpdsFeed> getSeries() {
        return feed(opdsV2FeedService.generateSeriesNavigation());
    }

    @Operation(summary = "Get OPDS 2.0 catalog feed", description = "Acquisition feed with pagination, search and filtering.")
    @ApiResponse(responseCode = "200", description = "Catalog feed returned successfully")
    @GetMapping(value = "/catalog", produces = FEED_MEDIA_TYPE)
    public ResponseEntity<OpdsFeed> getCatalog(@Parameter(hidden = true) HttpServletRequest request) {
        return feed(opdsV2FeedService.generateCatalogFeed(request));
    }

    @Operation(summary = "Get OPDS 2.0 recent books feed")
    @ApiResponse(responseCode = "200", description = "Recent feed returned successfully")
    @GetMapping(value = "/recent", produces = FEED_MEDIA_TYPE)
    public ResponseEntity<OpdsFeed> getRecent(@Parameter(hidden = true) HttpServletRequest request) {
        return feed(opdsV2FeedService.generateRecentFeed(request));
    }

    @Operation(summary = "Get OPDS 2.0 surprise feed")
    @ApiResponse(responseCode = "200", description = "Surprise feed returned successfully")
    @GetMapping(value = "/surprise", produces = FEED_MEDIA_TYPE)
    public ResponseEntity<OpdsFeed> getSurprise() {
        return feed(opdsV2FeedService.generateSurpriseFeed());
    }

    @Operation(summary = "Get OPDS 2.0 authentication document")
    @ApiResponse(responseCode = "200", description = "Authentication document returned successfully")
    @GetMapping(value = "/auth", produces = AUTH_MEDIA_TYPE)
    public ResponseEntity<OpdsAuthenticationDocument> getAuthenticationDocument() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(AUTH_MEDIA_TYPE))
                .body(opdsV2FeedService.generateAuthenticationDocument());
    }

    @Operation(summary = "Download book file", description = "Download a book file by its ID. Optionally specify a fileId to download a specific format.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book file downloaded successfully"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/{bookId}/download")
    public ResponseEntity<Resource> downloadBook(
            @Parameter(description = "ID of the book to download") @PathVariable("bookId") Long bookId,
            @Parameter(description = "Optional ID of a specific file format to download") @RequestParam(required = false) Long fileId) {
        opdsBookService.validateBookContentAccess(bookId, getOpdsUserId());
        if (fileId != null) {
            return bookDownloadService.downloadBookFile(bookId, fileId);
        }
        return bookService.downloadBook(bookId);
    }

    @Operation(summary = "Get book cover image", description = "Retrieve the cover image for a book by its ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cover image returned successfully"),
            @ApiResponse(responseCode = "404", description = "Book or cover not found")
    })
    @GetMapping("/{bookId}/cover")
    public ResponseEntity<Resource> getBookCover(@Parameter(description = "ID of the book") @PathVariable long bookId) {
        opdsBookService.validateBookContentAccess(bookId, getOpdsUserId());
        Resource coverImage = bookService.getBookThumbnail(bookId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + coverImage.getFilename() + "\"")
                .body(coverImage);
    }

    private ResponseEntity<OpdsFeed> feed(OpdsFeed feed) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(FEED_MEDIA_TYPE))
                .body(feed);
    }

    private Long getOpdsUserId() {
        OpdsUserDetails details = authenticationService.getOpdsUser();
        return details != null && details.getOpdsUserV2() != null
                ? details.getOpdsUserV2().getUserId()
                : null;
    }
}
