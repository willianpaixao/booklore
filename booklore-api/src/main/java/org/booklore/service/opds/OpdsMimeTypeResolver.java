package org.booklore.service.opds;

import org.booklore.model.dto.BookFile;
import org.booklore.util.ArchiveUtils;

import java.io.File;

/**
 * Resolves the MIME type for a {@link BookFile} used in OPDS acquisition links.
 * Shared by both the OPDS 1.2 (XML) and OPDS 2.0 (JSON) feed rendering, so the
 * archive-detection logic lives in one place.
 */
public final class OpdsMimeTypeResolver {

    private OpdsMimeTypeResolver() {
    }

    public static String resolve(BookFile bookFile) {
        if (bookFile == null || bookFile.getBookType() == null) {
            return "application/octet-stream";
        }
        return switch (bookFile.getBookType()) {
            case PDF -> "application/pdf";
            case EPUB -> "application/epub+zip";
            case FB2 -> {
                if (hasValidFilePath(bookFile)) {
                    ArchiveUtils.ArchiveType type = ArchiveUtils.detectArchiveType(new File(bookFile.getFilePath()));
                    if (type == ArchiveUtils.ArchiveType.ZIP) {
                        yield "application/zip";
                    }
                }
                yield "application/x-fictionbook+xml";
            }
            case MOBI -> "application/x-mobipocket-ebook";
            case AZW3 -> "application/vnd.amazon.ebook";
            case CBX -> {
                if (bookFile.getArchiveType() != null) {
                    if (bookFile.getArchiveType() == ArchiveUtils.ArchiveType.RAR) {
                        yield "application/vnd.comicbook-rar";
                    }
                    if (bookFile.getArchiveType() == ArchiveUtils.ArchiveType.ZIP) {
                        yield "application/vnd.comicbook+zip";
                    }
                    if (bookFile.getArchiveType() == ArchiveUtils.ArchiveType.SEVEN_ZIP) {
                        yield "application/x-7z-compressed";
                    }
                }

                if (hasValidFilePath(bookFile)) {
                    ArchiveUtils.ArchiveType type = ArchiveUtils.detectArchiveType(new File(bookFile.getFilePath()));
                    // We only trust detection if it found something definite (not UNKNOWN)
                    if (type != ArchiveUtils.ArchiveType.UNKNOWN) {
                        yield switch (type) {
                            case RAR -> "application/vnd.comicbook-rar";
                            case ZIP -> "application/vnd.comicbook+zip";
                            case SEVEN_ZIP -> "application/x-7z-compressed";
                            default -> "application/vnd.comicbook+zip"; // Should not happen given the if check
                        };
                    }
                }

                String lower = bookFile.getFileName().toLowerCase();
                if (lower.endsWith(".cbr")) yield "application/vnd.comicbook-rar";
                if (lower.endsWith(".cbz")) yield "application/vnd.comicbook+zip";
                if (lower.endsWith(".cb7")) yield "application/x-7z-compressed";
                if (lower.endsWith(".cbt")) yield "application/x-tar";
                yield "application/vnd.comicbook+zip";
            }
            case AUDIOBOOK -> {
                String lower = bookFile.getFileName().toLowerCase();
                if (lower.endsWith(".mp3")) yield "audio/mpeg";
                if (lower.endsWith(".opus")) yield "audio/opus";
                yield "audio/mp4";
            }
        };
    }

    private static boolean hasValidFilePath(BookFile bookFile) {
        return bookFile != null
                && bookFile.getFileName() != null
                && bookFile.getFilePath() != null;
    }
}
