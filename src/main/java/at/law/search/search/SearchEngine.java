package at.law.search.search;

import at.law.search.index.LuceneIndexer;
import at.law.search.journal.Journal;
import at.law.search.model.LegalDocument;
import at.law.search.model.LegalReference;
import at.law.search.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main search engine for Austrian law search.
 * Combines query parsing, Lucene search, and result ranking.
 */
public class SearchEngine {

    private static final Logger log = LoggerFactory.getLogger(SearchEngine.class);

    private final LuceneIndexer indexer;
    private final QueryParser queryParser;
    private final Journal journal;

    public SearchEngine(LuceneIndexer indexer, Journal journal) {
        this.indexer = indexer;
        this.queryParser = new QueryParser();
        this.journal = journal;
    }

    /**
     * Execute a natural language search.
     */
    public SearchResponse search(String query, SearchOptions options) {
        long startTime = System.currentTimeMillis();

        try {
            // Parse the query
            QueryParser.ParsedQuery parsed = queryParser.parse(query);
            journal.logSearchQuery(query, parsed.detectedLanguage().name());

            List<SearchResult> allResults = new ArrayList<>();

            // Search by direct legal references first
            for (LegalReference ref : parsed.references()) {
                List<SearchResult> refResults = indexer.searchByReference(ref, options.maxResults());
                for (SearchResult result : refResults) {
                    allResults.add(new SearchResult(
                        result.document(),
                        result.relevanceScore() * 1.5f, // Boost exact reference matches
                        result.highlights(),
                        List.of(ref),
                        SearchResult.MatchType.EXACT_REFERENCE
                    ));
                }
            }

            // Full-text search
            if (!parsed.normalizedQuery().isBlank()) {
                List<String> searchQueries = queryParser.generateSearchQueries(parsed);
                for (String searchQuery : searchQueries) {
                    try {
                        List<SearchResult> textResults = indexer.search(searchQuery, options.maxResults());
                        allResults.addAll(textResults);
                    } catch (Exception e) {
                        log.debug("Search query failed: {}", e.getMessage());
                    }
                }
            }

            // Filter by document type if specified
            if (options.documentType() != null) {
                allResults = allResults.stream()
                    .filter(r -> r.document().getDocumentType() == options.documentType())
                    .collect(Collectors.toList());
            }

            // Filter excluded terms
            for (String excluded : parsed.excludedTerms()) {
                String lowerExcluded = excluded.toLowerCase();
                allResults = allResults.stream()
                    .filter(r -> !r.document().getTitle().toLowerCase().contains(lowerExcluded))
                    .collect(Collectors.toList());
            }

            // Deduplicate and sort by relevance
            Map<String, SearchResult> uniqueResults = new LinkedHashMap<>();
            for (SearchResult result : allResults) {
                String id = result.document().getId();
                if (!uniqueResults.containsKey(id) ||
                    uniqueResults.get(id).relevanceScore() < result.relevanceScore()) {
                    uniqueResults.put(id, result);
                }
            }

            List<SearchResult> finalResults = uniqueResults.values().stream()
                .sorted((a, b) -> Float.compare(b.relevanceScore(), a.relevanceScore()))
                .limit(options.maxResults())
                .toList();

            long duration = System.currentTimeMillis() - startTime;
            journal.logSearchResult(query, finalResults.size(), duration);

            return new SearchResponse(
                parsed,
                finalResults,
                duration,
                null
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            journal.logError("Search", e);
            return new SearchResponse(
                null,
                List.of(),
                duration,
                e.getMessage()
            );
        }
    }

    /**
     * Search for related documents based on a source document.
     */
    public List<SearchResult> findRelated(LegalDocument document, int maxResults) {
        List<SearchResult> related = new ArrayList<>();

        try {
            // Search by references in the document
            for (LegalReference ref : document.getReferences()) {
                related.addAll(indexer.searchByReference(ref, maxResults / 2));
            }

            // Search by title keywords
            String titleQuery = document.getTitle()
                .replaceAll("[^a-zA-ZäöüÄÖÜß\\s]", " ")
                .trim();
            if (!titleQuery.isBlank()) {
                related.addAll(indexer.search(titleQuery, maxResults / 2));
            }

            // Remove the source document and deduplicate
            return related.stream()
                .filter(r -> !r.document().getId().equals(document.getId()))
                .collect(Collectors.collectingAndThen(
                    Collectors.toMap(
                        r -> r.document().getId(),
                        r -> r,
                        (a, b) -> a.relevanceScore() > b.relevanceScore() ? a : b
                    ),
                    map -> new ArrayList<>(map.values())
                ));

        } catch (Exception e) {
            journal.logError("Find related", e);
            return List.of();
        }
    }

    /**
     * Get statistics about the search index.
     */
    public IndexStats getIndexStats() {
        try {
            return new IndexStats(indexer.getDocumentCount(), true, null);
        } catch (IOException e) {
            return new IndexStats(0, false, e.getMessage());
        }
    }

    /**
     * Search options.
     */
    public record SearchOptions(
        int maxResults,
        LegalDocument.DocumentType documentType,
        boolean includeRelated
    ) {
        public static SearchOptions defaults() {
            return new SearchOptions(50, null, false);
        }

        public static SearchOptions forType(LegalDocument.DocumentType type) {
            return new SearchOptions(50, type, false);
        }
    }

    /**
     * Search response containing results and metadata.
     */
    public record SearchResponse(
        QueryParser.ParsedQuery parsedQuery,
        List<SearchResult> results,
        long durationMs,
        String error
    ) {
        public boolean isSuccess() {
            return error == null;
        }

        public int resultCount() {
            return results != null ? results.size() : 0;
        }
    }

    /**
     * Index statistics.
     */
    public record IndexStats(int documentCount, boolean healthy, String error) {}
}
