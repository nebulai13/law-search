package at.law.search.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a search result from the law search engine.
 * Contains the matched document, relevance score, and highlighted excerpts.
 */
public record SearchResult(
    LegalDocument document,
    float relevanceScore,
    List<String> highlights,       // Text excerpts with matches
    List<LegalReference> matchedReferences,
    MatchType matchType
) {
    public SearchResult {
        Objects.requireNonNull(document, "Document is required");
        highlights = highlights != null ? List.copyOf(highlights) : List.of();
        matchedReferences = matchedReferences != null ? List.copyOf(matchedReferences) : List.of();
    }

    /**
     * Type of match that produced this result.
     */
    public enum MatchType {
        EXACT_REFERENCE,      // Matched a specific § citation
        KEYWORD_MATCH,        // Matched search keywords
        SEMANTIC_MATCH,       // Matched by semantic similarity
        CROSS_REFERENCE,      // Found via cross-reference
        CASE_LAW_CITATION     // Found in court case citations
    }

    /**
     * Create a result for an exact legal reference match.
     */
    public static SearchResult exactMatch(LegalDocument doc, LegalReference ref) {
        return new SearchResult(doc, 1.0f, List.of(), List.of(ref), MatchType.EXACT_REFERENCE);
    }

    /**
     * Create a result for a keyword match with highlights.
     */
    public static SearchResult keywordMatch(LegalDocument doc, float score, List<String> highlights) {
        return new SearchResult(doc, score, highlights, List.of(), MatchType.KEYWORD_MATCH);
    }

    /**
     * Get a formatted summary of this search result.
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(String.format("%.2f", relevanceScore)).append("] ");
        sb.append(document.getCitation()).append("\n");
        sb.append("   ").append(document.getDocumentType().getGermanName());
        if (!highlights.isEmpty()) {
            sb.append("\n   ...").append(highlights.get(0)).append("...");
        }
        return sb.toString();
    }

    /**
     * Compare results by relevance score.
     */
    public int compareTo(SearchResult other) {
        return Float.compare(other.relevanceScore, this.relevanceScore);
    }
}
