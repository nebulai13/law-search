package at.law.search.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Represents an Austrian law (Gesetz) or regulation (Verordnung).
 * This includes federal laws (Bundesgesetze), state laws (Landesgesetze),
 * and constitutional laws (Verfassungsgesetze).
 */
public final class Law extends LegalDocument {

    private final String abbreviation;         // e.g., "VersG", "StGB"
    private final String bgblNumber;           // Bundesgesetzblatt number
    private final List<LawSection> sections;   // Structured sections/paragraphs
    private final String preamble;             // Law preamble if present
    private final LocalDate lastAmendment;     // Date of last amendment
    private final String consolidatedVersion;  // "Konsolidierte Fassung"

    private Law(Builder builder) {
        super(builder);
        this.abbreviation = builder.abbreviation;
        this.bgblNumber = builder.bgblNumber;
        this.sections = builder.sections != null ? List.copyOf(builder.sections) : List.of();
        this.preamble = builder.preamble;
        this.lastAmendment = builder.lastAmendment;
        this.consolidatedVersion = builder.consolidatedVersion;
    }

    public String getAbbreviation() { return abbreviation; }
    public String getBgblNumber() { return bgblNumber; }
    public List<LawSection> getSections() { return sections; }
    public String getPreamble() { return preamble; }
    public LocalDate getLastAmendment() { return lastAmendment; }
    public String getConsolidatedVersion() { return consolidatedVersion; }

    @Override
    public String getSummary() {
        return String.format("%s (%s) - %s",
            getTitle(),
            abbreviation != null ? abbreviation : "N/A",
            getDocumentType().getGermanName());
    }

    @Override
    public String getCitation() {
        if (bgblNumber != null) {
            return String.format("%s, BGBl. %s", getTitle(), bgblNumber);
        }
        return getTitle();
    }

    /**
     * Find a specific section by paragraph number.
     * @param paragraph e.g., "14", "2a"
     * @return The matching section or null
     */
    public LawSection findSection(String paragraph) {
        return sections.stream()
            .filter(s -> s.paragraph().equals(paragraph))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get all sections matching a subsection pattern.
     */
    public List<LawSection> findSections(String pattern) {
        return sections.stream()
            .filter(s -> s.paragraph().startsWith(pattern) ||
                        s.heading().toLowerCase().contains(pattern.toLowerCase()))
            .toList();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Represents a section/paragraph within a law.
     */
    public record LawSection(
        String paragraph,      // e.g., "14", "2a"
        String heading,        // Section heading
        String text,           // Full text of the section
        List<String> subsections, // Abs. 1, Abs. 2, etc.
        List<LegalReference> internalReferences // References to other sections
    ) {
        public LawSection {
            subsections = subsections != null ? List.copyOf(subsections) : List.of();
            internalReferences = internalReferences != null ? List.copyOf(internalReferences) : List.of();
        }

        /**
         * Get formatted section for display.
         */
        public String toFormattedText() {
            StringBuilder sb = new StringBuilder();
            sb.append("§ ").append(paragraph);
            if (heading != null && !heading.isBlank()) {
                sb.append(" ").append(heading);
            }
            sb.append("\n\n");
            sb.append(text);
            return sb.toString();
        }
    }

    public static class Builder extends LegalDocument.Builder<Builder> {
        private String abbreviation;
        private String bgblNumber;
        private List<LawSection> sections;
        private String preamble;
        private LocalDate lastAmendment;
        private String consolidatedVersion;

        @Override
        protected Builder self() { return this; }

        public Builder abbreviation(String abbr) { this.abbreviation = abbr; return this; }
        public Builder bgblNumber(String num) { this.bgblNumber = num; return this; }
        public Builder sections(List<LawSection> sections) { this.sections = sections; return this; }
        public Builder preamble(String preamble) { this.preamble = preamble; return this; }
        public Builder lastAmendment(LocalDate date) { this.lastAmendment = date; return this; }
        public Builder consolidatedVersion(String version) { this.consolidatedVersion = version; return this; }

        public Law build() {
            return new Law(this);
        }
    }
}
