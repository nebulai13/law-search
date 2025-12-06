package at.law.search.journal;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single entry in the work journal.
 * Used for tracking operations and enabling resumable sessions.
 */
public record JournalEntry(
    String id,
    EntryType type,
    LocalDateTime timestamp,
    Map<String, Object> data,
    String message
) {
    public JournalEntry {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString().substring(0, 12);
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        data = data != null ? Map.copyOf(data) : Map.of();
    }

    /**
     * Types of journal entries.
     */
    public enum EntryType {
        SESSION_START,
        SESSION_END,
        CRAWL_START,
        CRAWL_PROGRESS,
        CRAWL_COMPLETE,
        CRAWL_ERROR,
        INDEX_START,
        INDEX_COMPLETE,
        SEARCH_QUERY,
        SEARCH_RESULT,
        ERROR,
        INFO
    }

    /**
     * Create a session start entry.
     */
    public static JournalEntry sessionStart() {
        return new JournalEntry(
            null,
            EntryType.SESSION_START,
            null,
            Map.of("javaVersion", System.getProperty("java.version")),
            "Session started"
        );
    }

    /**
     * Create a crawl progress entry.
     */
    public static JournalEntry crawlProgress(String source, int processed, int total, String currentItem) {
        return new JournalEntry(
            null,
            EntryType.CRAWL_PROGRESS,
            null,
            Map.of(
                "source", source,
                "processed", processed,
                "total", total,
                "current", currentItem
            ),
            String.format("Crawling %s: %d/%d - %s", source, processed, total, currentItem)
        );
    }

    /**
     * Create an error entry.
     */
    public static JournalEntry error(String context, Exception e) {
        return new JournalEntry(
            null,
            EntryType.ERROR,
            null,
            Map.of(
                "context", context,
                "exception", e.getClass().getSimpleName(),
                "message", e.getMessage() != null ? e.getMessage() : ""
            ),
            String.format("Error in %s: %s", context, e.getMessage())
        );
    }

    /**
     * Create a search query entry.
     */
    public static JournalEntry searchQuery(String query, String language) {
        return new JournalEntry(
            null,
            EntryType.SEARCH_QUERY,
            null,
            Map.of("query", query, "language", language),
            String.format("Search: %s (%s)", query, language)
        );
    }

    /**
     * Create a search result entry.
     */
    public static JournalEntry searchResult(String query, int resultCount, long durationMs) {
        return new JournalEntry(
            null,
            EntryType.SEARCH_RESULT,
            null,
            Map.of("query", query, "resultCount", resultCount, "durationMs", durationMs),
            String.format("Found %d results for '%s' in %dms", resultCount, query, durationMs)
        );
    }
}
