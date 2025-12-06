package at.law.search.journal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Journaling system for tracking all operations.
 * Enables session resumability and provides audit trail.
 * Adapted from web-search-pro's journaling pattern.
 */
public class Journal implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Journal.class);
    private static final DateTimeFormatter SESSION_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final String sessionId;
    private final Path journalDir;
    private final Path journalFile;
    private final Path logFile;
    private final ObjectMapper mapper;
    private final List<JournalEntry> entries;
    private final LocalDateTime sessionStart;
    private final Map<String, Object> sessionStats;

    public Journal(Path baseDir) throws IOException {
        this.sessionStart = LocalDateTime.now();
        this.sessionId = SESSION_FORMAT.format(sessionStart) + "_" +
                        Integer.toHexString((int) System.currentTimeMillis()).substring(0, 6);
        this.journalDir = baseDir.resolve("journal");
        Files.createDirectories(journalDir);

        this.journalFile = journalDir.resolve("journal_" + sessionId + ".json");
        this.logFile = baseDir.resolve("logs").resolve("session_" + sessionId + ".log");
        Files.createDirectories(logFile.getParent());

        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.entries = new ArrayList<>();
        this.sessionStats = new ConcurrentHashMap<>();
        sessionStats.put("crawledDocuments", 0);
        sessionStats.put("indexedDocuments", 0);
        sessionStats.put("searchQueries", 0);
        sessionStats.put("errors", 0);

        addEntry(JournalEntry.sessionStart());
        log.info("Journal started: {}", sessionId);
    }

    public String getSessionId() { return sessionId; }
    public Path getJournalFile() { return journalFile; }
    public Path getLogFile() { return logFile; }

    /**
     * Add an entry to the journal.
     */
    public synchronized void addEntry(JournalEntry entry) {
        entries.add(entry);
        writeLog(entry.type().name(), entry.message());
        saveJournal();

        // Update stats
        switch (entry.type()) {
            case CRAWL_COMPLETE -> sessionStats.merge("crawledDocuments", 1, (a, b) -> (int) a + (int) b);
            case INDEX_COMPLETE -> sessionStats.merge("indexedDocuments", 1, (a, b) -> (int) a + (int) b);
            case SEARCH_QUERY -> sessionStats.merge("searchQueries", 1, (a, b) -> (int) a + (int) b);
            case ERROR -> sessionStats.merge("errors", 1, (a, b) -> (int) a + (int) b);
            default -> { }
        }
    }

    /**
     * Log crawl progress.
     */
    public void logCrawlProgress(String source, int processed, int total, String currentItem) {
        addEntry(JournalEntry.crawlProgress(source, processed, total, currentItem));
    }

    /**
     * Log an error.
     */
    public void logError(String context, Exception e) {
        addEntry(JournalEntry.error(context, e));
        log.error("Error in {}: {}", context, e.getMessage(), e);
    }

    /**
     * Log a search query.
     */
    public void logSearchQuery(String query, String language) {
        addEntry(JournalEntry.searchQuery(query, language));
    }

    /**
     * Log search results.
     */
    public void logSearchResult(String query, int resultCount, long durationMs) {
        addEntry(JournalEntry.searchResult(query, resultCount, durationMs));
    }

    /**
     * Log informational message.
     */
    public void info(String message) {
        addEntry(new JournalEntry(
            null,
            JournalEntry.EntryType.INFO,
            null,
            Map.of(),
            message
        ));
    }

    /**
     * Get session summary.
     */
    public Map<String, Object> getSummary() {
        var summary = new ConcurrentHashMap<>(sessionStats);
        summary.put("sessionId", sessionId);
        summary.put("startTime", sessionStart.toString());
        summary.put("duration", java.time.Duration.between(sessionStart, LocalDateTime.now()).toString());
        summary.put("entryCount", entries.size());
        return summary;
    }

    /**
     * Write a log line.
     */
    private void writeLog(String level, String message) {
        String logLine = String.format("[%s] [%s] %s%n",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            level,
            message);
        try {
            Files.writeString(logFile, logLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to write log: {}", e.getMessage());
        }
    }

    /**
     * Save journal to file.
     */
    private void saveJournal() {
        try {
            var journalData = Map.of(
                "sessionId", sessionId,
                "startTime", sessionStart,
                "entries", entries,
                "stats", sessionStats
            );
            mapper.writeValue(journalFile.toFile(), journalData);
        } catch (IOException e) {
            log.warn("Failed to save journal: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        addEntry(new JournalEntry(
            null,
            JournalEntry.EntryType.SESSION_END,
            null,
            getSummary(),
            "Session ended"
        ));
        log.info("Journal closed: {}", sessionId);
    }
}
