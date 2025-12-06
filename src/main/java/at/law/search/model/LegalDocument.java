package at.law.search.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Base class for all legal documents in the Austrian law system.
 * Represents laws, regulations, court decisions, and decrees.
 */
public sealed abstract class LegalDocument permits Law, CourtCase, Decree {

    private final String id;
    private final String title;
    private final String fullText;
    private final LocalDate publicationDate;
    private final LocalDate effectiveDate;
    private final String sourceUrl;
    private final DocumentType documentType;
    private final List<LegalReference> references;
    private final LocalDateTime indexedAt;

    protected LegalDocument(Builder<?> builder) {
        this.id = Objects.requireNonNull(builder.id, "Document ID is required");
        this.title = Objects.requireNonNull(builder.title, "Title is required");
        this.fullText = builder.fullText;
        this.publicationDate = builder.publicationDate;
        this.effectiveDate = builder.effectiveDate;
        this.sourceUrl = builder.sourceUrl;
        this.documentType = Objects.requireNonNull(builder.documentType, "Document type is required");
        this.references = builder.references != null ? List.copyOf(builder.references) : List.of();
        this.indexedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getFullText() { return fullText; }
    public LocalDate getPublicationDate() { return publicationDate; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public String getSourceUrl() { return sourceUrl; }
    public DocumentType getDocumentType() { return documentType; }
    public List<LegalReference> getReferences() { return references; }
    public LocalDateTime getIndexedAt() { return indexedAt; }

    /**
     * Get a short summary of the document for display purposes.
     */
    public abstract String getSummary();

    /**
     * Get the official citation format for this document.
     */
    public abstract String getCitation();

    /**
     * Check if document is currently in force.
     */
    public boolean isInForce() {
        return effectiveDate != null && !effectiveDate.isAfter(LocalDate.now());
    }

    public enum DocumentType {
        BUNDESGESETZ("Bundesgesetz", "Federal Law"),
        LANDESGESETZ("Landesgesetz", "State Law"),
        VERORDNUNG("Verordnung", "Regulation"),
        VERFASSUNG("Verfassungsgesetz", "Constitutional Law"),
        JUDIKATUR_VFGH("VfGH Erkenntnis", "Constitutional Court Decision"),
        JUDIKATUR_VWGH("VwGH Erkenntnis", "Administrative Court Decision"),
        JUDIKATUR_OGH("OGH Entscheidung", "Supreme Court Decision"),
        JUDIKATUR_BVWG("BVwG Entscheidung", "Federal Administrative Court Decision"),
        ERLASS("Erlass", "Decree");

        private final String germanName;
        private final String englishName;

        DocumentType(String germanName, String englishName) {
            this.germanName = germanName;
            this.englishName = englishName;
        }

        public String getGermanName() { return germanName; }
        public String getEnglishName() { return englishName; }
    }

    protected static abstract class Builder<T extends Builder<T>> {
        private String id;
        private String title;
        private String fullText;
        private LocalDate publicationDate;
        private LocalDate effectiveDate;
        private String sourceUrl;
        private DocumentType documentType;
        private List<LegalReference> references;

        protected abstract T self();

        public T id(String id) { this.id = id; return self(); }
        public T title(String title) { this.title = title; return self(); }
        public T fullText(String fullText) { this.fullText = fullText; return self(); }
        public T publicationDate(LocalDate date) { this.publicationDate = date; return self(); }
        public T effectiveDate(LocalDate date) { this.effectiveDate = date; return self(); }
        public T sourceUrl(String url) { this.sourceUrl = url; return self(); }
        public T documentType(DocumentType type) { this.documentType = type; return self(); }
        public T references(List<LegalReference> refs) { this.references = refs; return self(); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LegalDocument that = (LegalDocument) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
