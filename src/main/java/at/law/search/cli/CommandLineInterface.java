package at.law.search.cli;

import at.law.search.crawler.RisCrawler;
import at.law.search.index.LuceneIndexer;
import at.law.search.journal.Journal;
import at.law.search.model.LegalDocument;
import at.law.search.model.SearchResult;
import at.law.search.response.ResponseGenerator;
import at.law.search.search.SearchEngine;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Command-line interface for Austrian Law Search.
 * Provides interactive search and crawling capabilities.
 */
@Command(
    name = "law-search",
    mixinStandardHelpOptions = true,
    version = "Austrian Law Search 1.0.0",
    description = "Search Austrian laws, regulations, and court decisions"
)
public class CommandLineInterface implements Callable<Integer> {

    @Option(names = {"-q", "--query"}, description = "Search query")
    private String query;

    @Option(names = {"-c", "--crawl"}, description = "Crawl RIS database before searching")
    private boolean crawl;

    @Option(names = {"-i", "--interactive"}, description = "Interactive mode")
    private boolean interactive;

    @Option(names = {"-d", "--data-dir"}, description = "Data directory", defaultValue = "./data")
    private Path dataDir;

    @Option(names = {"-m", "--max-results"}, description = "Maximum results", defaultValue = "20")
    private int maxResults;

    @Option(names = {"-t", "--type"}, description = "Document type filter (BUNDESGESETZ, JUDIKATUR_VFGH, etc.)")
    private String documentType;

    @Option(names = {"--police-text"}, description = "Generate police/authority text")
    private boolean policeText;

    @Option(names = {"--max-pages"}, description = "Maximum pages to crawl per source", defaultValue = "1")
    private int maxPages;

    private Journal journal;
    private LuceneIndexer indexer;
    private SearchEngine searchEngine;
    private ResponseGenerator responseGenerator;
    private final ProgressDisplay progress = new ProgressDisplay();

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CommandLineInterface()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            initialize();

            if (crawl) {
                crawlDatabase();
            }

            if (interactive) {
                runInteractive();
            } else if (query != null && !query.isBlank()) {
                executeSearch(query);
            } else {
                System.out.println("Use --help for usage information, or --interactive for interactive mode");
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (journal != null) {
                journal.logError("Main", e);
            }
            return 1;
        } finally {
            cleanup();
        }
    }

    private void initialize() throws IOException {
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║      Austrian Law Search - Pocket Lawyer               ║");
        System.out.println("║      Rechtsinformationssystem Suche                    ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        journal = new Journal(dataDir);
        indexer = new LuceneIndexer(dataDir.resolve("index"));
        searchEngine = new SearchEngine(indexer, journal);
        responseGenerator = new ResponseGenerator();

        System.out.println("Session ID: " + journal.getSessionId());
        System.out.println("Data directory: " + dataDir.toAbsolutePath());

        SearchEngine.IndexStats stats = searchEngine.getIndexStats();
        System.out.println("Indexed documents: " + stats.documentCount());
        System.out.println();
    }

    private void crawlDatabase() {
        System.out.println("Starting RIS database crawl (max " + maxPages + " pages per source)...");
        System.out.println("This may take a while. Progress will be shown below.\n");

        try (RisCrawler crawler = new RisCrawler(journal, 500, maxPages)) {
            AtomicInteger totalIndexed = new AtomicInteger(0);

            // Crawl Bundesrecht
            System.out.println("▶ Crawling Bundesrecht (Federal Laws)...");
            crawler.crawlAll(
                RisCrawler.Application.BUNDESRECHT,
                doc -> {
                    try {
                        indexer.indexDocument(doc);
                        totalIndexed.incrementAndGet();
                    } catch (IOException e) {
                        journal.logError("Index", e);
                    }
                },
                prog -> progress.update(prog.source(), prog.processed(), prog.currentItem())
            );

            // Crawl VfGH
            System.out.println("\n▶ Crawling VfGH (Constitutional Court)...");
            crawler.crawlAll(
                RisCrawler.Application.VFGH,
                doc -> {
                    try {
                        indexer.indexDocument(doc);
                        totalIndexed.incrementAndGet();
                    } catch (IOException e) {
                        journal.logError("Index", e);
                    }
                },
                prog -> progress.update(prog.source(), prog.processed(), prog.currentItem())
            );

            // Commit all changes
            indexer.commit();

            System.out.println("\n\n✓ Crawl complete! Indexed " + totalIndexed.get() + " documents.");

        } catch (Exception e) {
            System.err.println("Crawl failed: " + e.getMessage());
            journal.logError("Crawl", e);
        }
    }

    private void runInteractive() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Interactive mode. Type your query or command:");
        System.out.println("  /help     - Show help");
        System.out.println("  /crawl    - Crawl RIS database");
        System.out.println("  /stats    - Show index statistics");
        System.out.println("  /police   - Toggle police text output");
        System.out.println("  /quit     - Exit");
        System.out.println();

        boolean showPoliceText = policeText;

        while (true) {
            System.out.print("\n🔍 > ");
            String input = scanner.nextLine().trim();

            if (input.isBlank()) continue;

            if (input.startsWith("/")) {
                switch (input.toLowerCase()) {
                    case "/quit", "/exit", "/q" -> {
                        System.out.println("Auf Wiedersehen!");
                        return;
                    }
                    case "/help", "/h" -> printHelp();
                    case "/crawl" -> crawlDatabase();
                    case "/stats" -> printStats();
                    case "/police" -> {
                        showPoliceText = !showPoliceText;
                        System.out.println("Police text output: " + (showPoliceText ? "ON" : "OFF"));
                    }
                    default -> System.out.println("Unknown command. Type /help for available commands.");
                }
            } else {
                executeSearch(input, showPoliceText);
            }
        }
    }

    private void executeSearch(String searchQuery) {
        executeSearch(searchQuery, policeText);
    }

    private void executeSearch(String searchQuery, boolean showPoliceText) {
        System.out.println("\nSearching: " + searchQuery);
        System.out.println("─".repeat(50));

        LegalDocument.DocumentType typeFilter = null;
        if (documentType != null) {
            try {
                typeFilter = LegalDocument.DocumentType.valueOf(documentType.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.out.println("Warning: Unknown document type '" + documentType + "', ignoring filter");
            }
        }

        SearchEngine.SearchOptions options = new SearchEngine.SearchOptions(
            maxResults, typeFilter, false
        );

        SearchEngine.SearchResponse response = searchEngine.search(searchQuery, options);

        if (!response.isSuccess()) {
            System.out.println("Search error: " + response.error());
            return;
        }

        if (response.results().isEmpty()) {
            System.out.println("No results found.");
            return;
        }

        System.out.println("Found " + response.resultCount() + " results in " +
                          response.durationMs() + "ms\n");

        // Display results
        int rank = 1;
        for (SearchResult result : response.results()) {
            System.out.printf("[%d] %.2f - %s%n", rank++, result.relevanceScore(), result.document().getCitation());
            System.out.println("    " + result.document().getDocumentType().getGermanName());
            if (!result.highlights().isEmpty()) {
                System.out.println("    ..." + result.highlights().get(0) + "...");
            }
            System.out.println();
        }

        // Generate and display comprehensive response
        if (showPoliceText) {
            ResponseGenerator.LegalResponse legalResponse =
                responseGenerator.generateResponse(searchQuery, response);

            System.out.println("\n" + legalResponse.authorityText());
        }
    }

    private void printHelp() {
        System.out.println("""

            Austrian Law Search - Help
            ══════════════════════════

            Search Examples:
              § 14 VersG                    - Search by paragraph reference
              Versammlungsfreiheit          - Search by keyword (German)
              demonstration rights          - Search by keyword (English)
              +spontan +versammlung -anmeldung  - Required/excluded terms

            Commands:
              /help     - Show this help
              /crawl    - Crawl RIS database to update index
              /stats    - Show index statistics
              /police   - Toggle police/authority text output
              /quit     - Exit the program

            Search Operators:
              +term     - Term must be present
              -term     - Term must not be present
              "phrase"  - Exact phrase match

            """);
    }

    private void printStats() {
        SearchEngine.IndexStats stats = searchEngine.getIndexStats();
        System.out.println("\nIndex Statistics");
        System.out.println("────────────────");
        System.out.println("Documents indexed: " + stats.documentCount());
        System.out.println("Index healthy: " + stats.healthy());
        if (stats.error() != null) {
            System.out.println("Error: " + stats.error());
        }

        var summary = journal.getSummary();
        System.out.println("\nSession Statistics");
        System.out.println("──────────────────");
        System.out.println("Session ID: " + summary.get("sessionId"));
        System.out.println("Duration: " + summary.get("duration"));
        System.out.println("Search queries: " + summary.get("searchQueries"));
        System.out.println("Errors: " + summary.get("errors"));
    }

    private void cleanup() {
        try {
            if (journal != null) journal.close();
            if (indexer != null) indexer.close();
        } catch (Exception e) {
            System.err.println("Cleanup error: " + e.getMessage());
        }
    }

    /**
     * Simple progress display for terminal.
     */
    static class ProgressDisplay {
        private int lastLength = 0;

        void update(String source, int count, String current) {
            String message = String.format("  [%s] %d documents - %s",
                source, count, truncate(current, 40));

            // Clear previous line and write new
            System.out.print("\r" + " ".repeat(lastLength) + "\r");
            System.out.print(message);
            lastLength = message.length();
        }

        private String truncate(String s, int max) {
            return s.length() <= max ? s : s.substring(0, max - 3) + "...";
        }
    }
}
