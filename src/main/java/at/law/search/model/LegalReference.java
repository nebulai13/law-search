package at.law.search.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a reference to a specific section of Austrian law.
 * Handles Austrian legal citation formats like "§ 14 VersG" or "Art. 10 B-VG".
 */
public record LegalReference(
    String paragraph,      // e.g., "14", "10a", "1"
    String lawAbbreviation, // e.g., "VersG", "StGB", "B-VG"
    String lawFullName,     // e.g., "Versammlungsgesetz"
    ReferenceType type,
    String subsection,      // e.g., "Abs. 1", "Z 3"
    String targetDocumentId // ID of referenced document if known
) {
    // Pattern for Austrian law references: § 14 Abs. 1 VersG
    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
        "(?:§|Art\\.?)\\s*(\\d+[a-z]?)\\s*" +           // § or Art. followed by number
        "(?:(Abs\\.?\\s*\\d+|Z\\s*\\d+|lit\\.?\\s*[a-z])\\s*)?" + // Optional subsection
        "([A-Za-zÄÖÜäöü\\-]+(?:\\s+\\d{4})?)"          // Law abbreviation
    );

    public enum ReferenceType {
        PARAGRAPH("§"),      // Standard law paragraph
        ARTICLE("Art."),     // Constitutional articles
        SECTION("Abschnitt"); // Sections

        private final String prefix;
        ReferenceType(String prefix) { this.prefix = prefix; }
        public String getPrefix() { return prefix; }
    }

    /**
     * Parse a legal reference string into a LegalReference object.
     * @param referenceText Text like "§ 14 Abs. 1 VersG"
     * @return Parsed reference or null if not parseable
     */
    public static LegalReference parse(String referenceText) {
        if (referenceText == null || referenceText.isBlank()) {
            return null;
        }

        Matcher matcher = REFERENCE_PATTERN.matcher(referenceText.trim());
        if (matcher.find()) {
            String paragraph = matcher.group(1);
            String subsection = matcher.group(2);
            String lawAbbr = matcher.group(3);

            ReferenceType type = referenceText.contains("Art")
                ? ReferenceType.ARTICLE
                : ReferenceType.PARAGRAPH;

            return new LegalReference(
                paragraph,
                lawAbbr != null ? lawAbbr.trim() : null,
                null, // Full name would be looked up
                type,
                subsection,
                null  // Target document resolved later
            );
        }
        return null;
    }

    /**
     * Get the formatted citation string.
     */
    public String toCitation() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getPrefix()).append(" ").append(paragraph);
        if (subsection != null && !subsection.isBlank()) {
            sb.append(" ").append(subsection);
        }
        if (lawAbbreviation != null) {
            sb.append(" ").append(lawAbbreviation);
        }
        return sb.toString();
    }

    /**
     * Check if this reference matches a given law abbreviation.
     */
    public boolean matchesLaw(String abbreviation) {
        return lawAbbreviation != null &&
               lawAbbreviation.equalsIgnoreCase(abbreviation);
    }

    @Override
    public String toString() {
        return toCitation();
    }

    // Common Austrian law abbreviations for reference
    public static final class CommonLaws {
        public static final String VERSG = "VersG";        // Versammlungsgesetz
        public static final String STGB = "StGB";          // Strafgesetzbuch
        public static final String ABGB = "ABGB";          // Allgemeines Bürgerliches Gesetzbuch
        public static final String BVG = "B-VG";           // Bundes-Verfassungsgesetz
        public static final String STPO = "StPO";          // Strafprozessordnung
        public static final String AVRAG = "AVRAG";        // Arbeitsvertragsrechts-Anpassungsgesetz
        public static final String SPG = "SPG";            // Sicherheitspolizeigesetz
        public static final String VSTVO = "VStVO";        // Verwaltungsstrafverordnung
        public static final String EMRK = "EMRK";          // Europäische Menschenrechtskonvention

        private CommonLaws() {}
    }
}
