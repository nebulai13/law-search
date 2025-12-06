package at.law.search.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Represents an Austrian decree (Erlass) or official interpretation.
 * Decrees are administrative instructions that interpret how laws should be applied.
 */
public final class Decree extends LegalDocument {

    private final String issuingAuthority;    // Ministry or authority that issued the decree
    private final String fileReference;        // Aktenzahl
    private final List<LegalReference> interpretsLaws; // Which laws this decree interprets
    private final String addressees;           // Who the decree is directed to

    private Decree(Builder builder) {
        super(builder);
        this.issuingAuthority = builder.issuingAuthority;
        this.fileReference = builder.fileReference;
        this.interpretsLaws = builder.interpretsLaws != null ? List.copyOf(builder.interpretsLaws) : List.of();
        this.addressees = builder.addressees;
    }

    public String getIssuingAuthority() { return issuingAuthority; }
    public String getFileReference() { return fileReference; }
    public List<LegalReference> getInterpretsLaws() { return interpretsLaws; }
    public String getAddressees() { return addressees; }

    @Override
    public String getSummary() {
        return String.format("Erlass: %s (%s)", getTitle(),
            fileReference != null ? fileReference : "N/A");
    }

    @Override
    public String getCitation() {
        StringBuilder sb = new StringBuilder();
        sb.append(getTitle());
        if (issuingAuthority != null) {
            sb.append(", ").append(issuingAuthority);
        }
        if (fileReference != null) {
            sb.append(", GZ ").append(fileReference);
        }
        return sb.toString();
    }

    /**
     * Check if this decree interprets a specific law.
     */
    public boolean interpretsLaw(String lawAbbreviation) {
        return interpretsLaws.stream()
            .anyMatch(ref -> ref.matchesLaw(lawAbbreviation));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends LegalDocument.Builder<Builder> {
        private String issuingAuthority;
        private String fileReference;
        private List<LegalReference> interpretsLaws;
        private String addressees;

        @Override
        protected Builder self() { return this; }

        public Builder issuingAuthority(String authority) { this.issuingAuthority = authority; return this; }
        public Builder fileReference(String ref) { this.fileReference = ref; return this; }
        public Builder interpretsLaws(List<LegalReference> laws) { this.interpretsLaws = laws; return this; }
        public Builder addressees(String addressees) { this.addressees = addressees; return this; }

        public Decree build() {
            return new Decree(this);
        }
    }
}
