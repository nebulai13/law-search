package at.law.search.response;

import at.law.search.model.*;
import at.law.search.search.SearchEngine;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates context-aware responses for legal queries.
 * Creates structured output including law excerpts, case citations,
 * and specialized text for authorities (e.g., police).
 */
public class ResponseGenerator {

    /**
     * Generate a comprehensive response for a legal query.
     */
    public LegalResponse generateResponse(String query, SearchEngine.SearchResponse searchResponse) {
        if (!searchResponse.isSuccess() || searchResponse.results().isEmpty()) {
            return LegalResponse.empty(query, searchResponse.error());
        }

        // Categorize results by type
        Map<LegalDocument.DocumentType, List<SearchResult>> byType = searchResponse.results().stream()
            .collect(Collectors.groupingBy(r -> r.document().getDocumentType()));

        // Extract relevant laws
        List<LawExcerpt> lawExcerpts = extractLawExcerpts(
            byType.getOrDefault(LegalDocument.DocumentType.BUNDESGESETZ, List.of())
        );

        // Extract relevant court cases
        List<CaseReference> caseReferences = extractCaseReferences(
            byType.values().stream()
                .flatMap(List::stream)
                .filter(r -> r.document() instanceof CourtCase)
                .toList()
        );

        // Generate plain language summary
        String summary = generateSummary(query, searchResponse.results());

        // Generate authority text (for police, officials, etc.)
        String authorityText = generateAuthorityText(query, lawExcerpts, caseReferences);

        // Collect all references
        List<LegalReference> allReferences = searchResponse.results().stream()
            .flatMap(r -> r.matchedReferences().stream())
            .distinct()
            .toList();

        return new LegalResponse(
            query,
            summary,
            lawExcerpts,
            caseReferences,
            allReferences,
            authorityText,
            null
        );
    }

    /**
     * Extract relevant law excerpts from search results.
     */
    private List<LawExcerpt> extractLawExcerpts(List<SearchResult> lawResults) {
        return lawResults.stream()
            .filter(r -> r.document() instanceof Law)
            .map(r -> {
                Law law = (Law) r.document();
                String relevantText = extractRelevantParagraphs(law, r.highlights());

                return new LawExcerpt(
                    law.getCitation(),
                    law.getAbbreviation(),
                    law.getTitle(),
                    relevantText,
                    r.matchedReferences(),
                    law.getSourceUrl()
                );
            })
            .toList();
    }

    /**
     * Extract relevant paragraphs from a law.
     */
    private String extractRelevantParagraphs(Law law, List<String> highlights) {
        if (law.getFullText() == null) {
            return highlights.isEmpty() ? law.getTitle() : String.join("\n", highlights);
        }

        // For now, return highlights or a portion of the full text
        if (!highlights.isEmpty()) {
            return String.join("\n...\n", highlights);
        }

        String fullText = law.getFullText();
        return fullText.length() > 500 ? fullText.substring(0, 500) + "..." : fullText;
    }

    /**
     * Extract case references from search results.
     */
    private List<CaseReference> extractCaseReferences(List<SearchResult> caseResults) {
        return caseResults.stream()
            .filter(r -> r.document() instanceof CourtCase)
            .map(r -> {
                CourtCase courtCase = (CourtCase) r.document();
                return new CaseReference(
                    courtCase.getCitation(),
                    courtCase.getCourt().getGermanName(),
                    courtCase.getDecisionDate(),
                    courtCase.getHeadnotes(),
                    courtCase.getAppliedLaws(),
                    courtCase.getSourceUrl()
                );
            })
            .toList();
    }

    /**
     * Generate a plain language summary of the search results.
     */
    private String generateSummary(String query, List<SearchResult> results) {
        StringBuilder summary = new StringBuilder();

        summary.append("Zu Ihrer Anfrage \"").append(query).append("\" wurden ")
               .append(results.size()).append(" relevante Dokumente gefunden.\n\n");

        // Count by type
        Map<LegalDocument.DocumentType, Long> typeCounts = results.stream()
            .collect(Collectors.groupingBy(
                r -> r.document().getDocumentType(),
                Collectors.counting()
            ));

        if (typeCounts.containsKey(LegalDocument.DocumentType.BUNDESGESETZ)) {
            summary.append("- ").append(typeCounts.get(LegalDocument.DocumentType.BUNDESGESETZ))
                   .append(" Bundesgesetze\n");
        }
        if (typeCounts.containsKey(LegalDocument.DocumentType.JUDIKATUR_VFGH) ||
            typeCounts.containsKey(LegalDocument.DocumentType.JUDIKATUR_VWGH) ||
            typeCounts.containsKey(LegalDocument.DocumentType.JUDIKATUR_OGH)) {
            long caseCount = typeCounts.entrySet().stream()
                .filter(e -> e.getKey().name().startsWith("JUDIKATUR"))
                .mapToLong(Map.Entry::getValue)
                .sum();
            summary.append("- ").append(caseCount).append(" Gerichtsentscheidungen\n");
        }

        return summary.toString();
    }

    /**
     * Generate official text for authorities (police, officials).
     * This is the "pocket lawyer" feature - text that can be shown to police.
     */
    private String generateAuthorityText(String query, List<LawExcerpt> laws, List<CaseReference> cases) {
        StringBuilder text = new StringBuilder();

        text.append("═══════════════════════════════════════════════════════════════\n");
        text.append("                    RECHTLICHE GRUNDLAGEN\n");
        text.append("═══════════════════════════════════════════════════════════════\n\n");

        // Add relevant law excerpts
        if (!laws.isEmpty()) {
            text.append("GESETZLICHE BESTIMMUNGEN:\n");
            text.append("─────────────────────────\n\n");

            for (LawExcerpt excerpt : laws) {
                text.append("▶ ").append(excerpt.citation()).append("\n");
                if (excerpt.abbreviation() != null) {
                    text.append("  (").append(excerpt.abbreviation()).append(")\n");
                }
                text.append("\n");
                text.append(excerpt.relevantText()).append("\n\n");
            }
        }

        // Add case law references
        if (!cases.isEmpty()) {
            text.append("\nRELEVANTE JUDIKATUR:\n");
            text.append("────────────────────\n\n");

            for (CaseReference caseRef : cases) {
                text.append("▶ ").append(caseRef.citation());
                if (caseRef.decisionDate() != null) {
                    text.append(" vom ").append(caseRef.decisionDate());
                }
                text.append("\n");
                text.append("  ").append(caseRef.court()).append("\n");
                if (caseRef.headnotes() != null) {
                    text.append("  Rechtssatz: ").append(truncate(caseRef.headnotes(), 200)).append("\n");
                }
                text.append("\n");
            }
        }

        text.append("\n═══════════════════════════════════════════════════════════════\n");
        text.append("Diese Informationen dienen der rechtlichen Orientierung.\n");
        text.append("Für verbindliche Auskünfte konsultieren Sie einen Rechtsanwalt.\n");
        text.append("═══════════════════════════════════════════════════════════════\n");

        return text.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Full legal response with all components.
     */
    public record LegalResponse(
        String query,
        String summary,
        List<LawExcerpt> lawExcerpts,
        List<CaseReference> caseReferences,
        List<LegalReference> allReferences,
        String authorityText,
        String error
    ) {
        public static LegalResponse empty(String query, String error) {
            return new LegalResponse(query, null, List.of(), List.of(), List.of(), null,
                error != null ? error : "Keine Ergebnisse gefunden");
        }

        public boolean hasResults() {
            return !lawExcerpts.isEmpty() || !caseReferences.isEmpty();
        }
    }

    /**
     * Excerpt from a law with citation and relevant text.
     */
    public record LawExcerpt(
        String citation,
        String abbreviation,
        String title,
        String relevantText,
        List<LegalReference> paragraphs,
        String sourceUrl
    ) {}

    /**
     * Reference to a court case.
     */
    public record CaseReference(
        String citation,
        String court,
        java.time.LocalDate decisionDate,
        String headnotes,
        List<LegalReference> appliedLaws,
        String sourceUrl
    ) {}
}
