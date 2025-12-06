package at.law.search.search;

import at.law.search.model.LegalReference;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for natural language legal queries in German and English.
 * Supports advanced search syntax and legal reference detection.
 */
public class QueryParser {

    // Patterns for detecting legal references
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile(
        "§\\s*(\\d+[a-z]?)(?:\\s+(Abs\\.?|Z|lit\\.?)\\s*(\\d+|[a-z]))?(?:\\s+([A-Za-zÄÖÜäöü\\-]+))?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
        "Art\\.?\\s*(\\d+)(?:\\s+([A-Za-zÄÖÜäöü\\-]+))?",
        Pattern.CASE_INSENSITIVE
    );

    // Common German legal terms with English translations
    private static final Map<String, String> GERMAN_TO_ENGLISH = Map.ofEntries(
        Map.entry("versammlung", "assembly"),
        Map.entry("demonstration", "demonstration"),
        Map.entry("kundgebung", "rally"),
        Map.entry("grundrecht", "fundamental right"),
        Map.entry("meinungsfreiheit", "freedom of speech"),
        Map.entry("versammlungsfreiheit", "freedom of assembly"),
        Map.entry("polizei", "police"),
        Map.entry("auflösung", "dissolution"),
        Map.entry("genehmigung", "permit"),
        Map.entry("anmeldung", "registration"),
        Map.entry("spontanversammlung", "spontaneous assembly"),
        Map.entry("strafrecht", "criminal law"),
        Map.entry("verwaltungsrecht", "administrative law"),
        Map.entry("verfassungsrecht", "constitutional law")
    );

    private static final Map<String, String> ENGLISH_TO_GERMAN = new HashMap<>();
    static {
        GERMAN_TO_ENGLISH.forEach((k, v) -> ENGLISH_TO_GERMAN.put(v.toLowerCase(), k));
    }

    /**
     * Parsed query result.
     */
    public record ParsedQuery(
        String originalQuery,
        String normalizedQuery,
        List<String> keywords,
        List<LegalReference> references,
        List<String> requiredTerms,    // Terms marked with +
        List<String> excludedTerms,    // Terms marked with -
        QueryLanguage detectedLanguage,
        Map<String, String> filters    // site:, filetype:, etc.
    ) {}

    public enum QueryLanguage {
        GERMAN, ENGLISH, UNKNOWN
    }

    /**
     * Parse a natural language query.
     */
    public ParsedQuery parse(String query) {
        if (query == null || query.isBlank()) {
            return new ParsedQuery("", "", List.of(), List.of(), List.of(), List.of(),
                QueryLanguage.UNKNOWN, Map.of());
        }

        String original = query.trim();
        List<String> keywords = new ArrayList<>();
        List<LegalReference> references = new ArrayList<>();
        List<String> requiredTerms = new ArrayList<>();
        List<String> excludedTerms = new ArrayList<>();
        Map<String, String> filters = new HashMap<>();

        // Detect language
        QueryLanguage language = detectLanguage(original);

        // Extract legal references
        extractReferences(original, references);

        // Parse advanced syntax
        String remaining = parseAdvancedSyntax(original, requiredTerms, excludedTerms, filters);

        // Extract keywords
        keywords.addAll(extractKeywords(remaining));

        // Normalize query for search
        String normalized = normalizeQuery(remaining, language);

        return new ParsedQuery(original, normalized, keywords, references,
            requiredTerms, excludedTerms, language, filters);
    }

    /**
     * Detect query language.
     */
    private QueryLanguage detectLanguage(String query) {
        String lower = query.toLowerCase();

        // Check for German-specific characters and words
        if (lower.contains("ä") || lower.contains("ö") || lower.contains("ü") || lower.contains("ß")) {
            return QueryLanguage.GERMAN;
        }

        // Check for German legal terms
        for (String germanTerm : GERMAN_TO_ENGLISH.keySet()) {
            if (lower.contains(germanTerm)) {
                return QueryLanguage.GERMAN;
            }
        }

        // Check for English legal terms
        for (String englishTerm : ENGLISH_TO_GERMAN.keySet()) {
            if (lower.contains(englishTerm)) {
                return QueryLanguage.ENGLISH;
            }
        }

        // Default to German for Austrian law context
        return QueryLanguage.GERMAN;
    }

    /**
     * Extract legal references from query.
     */
    private void extractReferences(String query, List<LegalReference> references) {
        // Extract paragraph references
        Matcher paraMatcher = PARAGRAPH_PATTERN.matcher(query);
        while (paraMatcher.find()) {
            String paragraph = paraMatcher.group(1);
            String subsectionType = paraMatcher.group(2);
            String subsectionNum = paraMatcher.group(3);
            String lawAbbr = paraMatcher.group(4);

            String subsection = null;
            if (subsectionType != null && subsectionNum != null) {
                subsection = subsectionType + " " + subsectionNum;
            }

            references.add(new LegalReference(
                paragraph,
                lawAbbr,
                null,
                LegalReference.ReferenceType.PARAGRAPH,
                subsection,
                null
            ));
        }

        // Extract article references
        Matcher artMatcher = ARTICLE_PATTERN.matcher(query);
        while (artMatcher.find()) {
            String article = artMatcher.group(1);
            String lawAbbr = artMatcher.group(2);

            references.add(new LegalReference(
                article,
                lawAbbr,
                null,
                LegalReference.ReferenceType.ARTICLE,
                null,
                null
            ));
        }
    }

    /**
     * Parse advanced search syntax.
     */
    private String parseAdvancedSyntax(String query, List<String> required,
                                        List<String> excluded, Map<String, String> filters) {
        StringBuilder remaining = new StringBuilder();
        String[] tokens = query.split("\\s+");

        for (String token : tokens) {
            if (token.startsWith("+")) {
                required.add(token.substring(1));
            } else if (token.startsWith("-") && token.length() > 1) {
                excluded.add(token.substring(1));
            } else if (token.contains(":")) {
                String[] parts = token.split(":", 2);
                if (parts.length == 2) {
                    filters.put(parts[0].toLowerCase(), parts[1]);
                }
            } else if (!token.isBlank()) {
                remaining.append(token).append(" ");
            }
        }

        return remaining.toString().trim();
    }

    /**
     * Extract meaningful keywords from text.
     */
    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();

        // Remove quoted phrases first
        Pattern quotePattern = Pattern.compile("\"([^\"]+)\"");
        Matcher quoteMatcher = quotePattern.matcher(text);
        while (quoteMatcher.find()) {
            keywords.add(quoteMatcher.group(1));
        }

        // Get remaining words
        String withoutQuotes = text.replaceAll("\"[^\"]+\"", "");
        String[] words = withoutQuotes.split("\\s+");

        for (String word : words) {
            if (word.length() > 2 && !isStopWord(word)) {
                keywords.add(word.toLowerCase());
            }
        }

        return keywords;
    }

    /**
     * Check if word is a stop word.
     */
    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of(
            // German
            "der", "die", "das", "und", "oder", "ein", "eine", "einer", "eines",
            "in", "im", "an", "am", "auf", "für", "mit", "bei", "nach", "von", "zu",
            "ist", "sind", "war", "waren", "wird", "werden", "hat", "haben",
            "ich", "du", "er", "sie", "es", "wir", "ihr",
            // English (excluding duplicates with German: "an", "in")
            "the", "a", "and", "or", "on", "at", "for", "with", "by",
            "to", "of", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did"
        );
        return stopWords.contains(word.toLowerCase());
    }

    /**
     * Normalize query for search engine.
     */
    private String normalizeQuery(String query, QueryLanguage language) {
        String normalized = query.toLowerCase().trim();

        // Translate if English to German (since laws are in German)
        if (language == QueryLanguage.ENGLISH) {
            for (Map.Entry<String, String> entry : ENGLISH_TO_GERMAN.entrySet()) {
                normalized = normalized.replace(entry.getKey(), entry.getValue());
            }
        }

        return normalized;
    }

    /**
     * Generate search queries for different contexts.
     */
    public List<String> generateSearchQueries(ParsedQuery parsed) {
        List<String> queries = new ArrayList<>();

        // Original normalized query
        queries.add(parsed.normalizedQuery());

        // Add reference-based queries
        for (LegalReference ref : parsed.references()) {
            queries.add(ref.toCitation());
        }

        // Add keyword combinations
        if (parsed.keywords().size() > 1) {
            queries.add(String.join(" AND ", parsed.keywords()));
        }

        return queries;
    }
}
