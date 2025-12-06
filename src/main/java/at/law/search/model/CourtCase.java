package at.law.search.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Represents an Austrian court decision (Judikatur).
 * Includes decisions from VfGH, VwGH, OGH, and other courts.
 */
public final class CourtCase extends LegalDocument {

    private final String caseNumber;          // Geschäftszahl, e.g., "G 9/2022"
    private final Court court;                // Which court issued the decision
    private final LocalDate decisionDate;     // Date of decision
    private final String headnotes;           // Rechtssätze/Leitsätze
    private final List<String> legalIssues;   // Legal questions addressed
    private final CaseOutcome outcome;        // How the case was decided
    private final List<LegalReference> appliedLaws; // Laws cited in decision
    private final String parties;             // Anonymized party information

    private CourtCase(Builder builder) {
        super(builder);
        this.caseNumber = Objects.requireNonNull(builder.caseNumber, "Case number required");
        this.court = Objects.requireNonNull(builder.court, "Court is required");
        this.decisionDate = builder.decisionDate;
        this.headnotes = builder.headnotes;
        this.legalIssues = builder.legalIssues != null ? List.copyOf(builder.legalIssues) : List.of();
        this.outcome = builder.outcome;
        this.appliedLaws = builder.appliedLaws != null ? List.copyOf(builder.appliedLaws) : List.of();
        this.parties = builder.parties;
    }

    public String getCaseNumber() { return caseNumber; }
    public Court getCourt() { return court; }
    public LocalDate getDecisionDate() { return decisionDate; }
    public String getHeadnotes() { return headnotes; }
    public List<String> getLegalIssues() { return legalIssues; }
    public CaseOutcome getOutcome() { return outcome; }
    public List<LegalReference> getAppliedLaws() { return appliedLaws; }
    public String getParties() { return parties; }

    @Override
    public String getSummary() {
        return String.format("%s %s vom %s",
            court.getAbbreviation(),
            caseNumber,
            decisionDate != null ? decisionDate.toString() : "N/A");
    }

    @Override
    public String getCitation() {
        return String.format("%s %s", court.getAbbreviation(), caseNumber);
    }

    /**
     * Check if this case cites a specific law.
     */
    public boolean citesLaw(String lawAbbreviation) {
        return appliedLaws.stream()
            .anyMatch(ref -> ref.matchesLaw(lawAbbreviation));
    }

    /**
     * Austrian courts hierarchy.
     */
    public enum Court {
        VFGH("VfGH", "Verfassungsgerichtshof", "Constitutional Court"),
        VWGH("VwGH", "Verwaltungsgerichtshof", "Administrative Court"),
        OGH("OGH", "Oberster Gerichtshof", "Supreme Court"),
        BVWG("BVwG", "Bundesverwaltungsgericht", "Federal Administrative Court"),
        LVG("LVwG", "Landesverwaltungsgericht", "State Administrative Court"),
        OLG("OLG", "Oberlandesgericht", "Higher Regional Court"),
        LG("LG", "Landesgericht", "Regional Court"),
        BG("BG", "Bezirksgericht", "District Court"),
        DSB("DSB", "Datenschutzbehörde", "Data Protection Authority");

        private final String abbreviation;
        private final String germanName;
        private final String englishName;

        Court(String abbreviation, String germanName, String englishName) {
            this.abbreviation = abbreviation;
            this.germanName = germanName;
            this.englishName = englishName;
        }

        public String getAbbreviation() { return abbreviation; }
        public String getGermanName() { return germanName; }
        public String getEnglishName() { return englishName; }
    }

    /**
     * Possible case outcomes.
     */
    public enum CaseOutcome {
        GRANTED("stattgegeben", "Granted"),
        DENIED("abgewiesen", "Denied"),
        PARTIALLY_GRANTED("teilweise stattgegeben", "Partially Granted"),
        DISMISSED("zurückgewiesen", "Dismissed"),
        REFERRED("verwiesen", "Referred"),
        ANNULLED("aufgehoben", "Annulled"),
        MODIFIED("abgeändert", "Modified");

        private final String germanName;
        private final String englishName;

        CaseOutcome(String germanName, String englishName) {
            this.germanName = germanName;
            this.englishName = englishName;
        }

        public String getGermanName() { return germanName; }
        public String getEnglishName() { return englishName; }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends LegalDocument.Builder<Builder> {
        private String caseNumber;
        private Court court;
        private LocalDate decisionDate;
        private String headnotes;
        private List<String> legalIssues;
        private CaseOutcome outcome;
        private List<LegalReference> appliedLaws;
        private String parties;

        @Override
        protected Builder self() { return this; }

        public Builder caseNumber(String num) { this.caseNumber = num; return this; }
        public Builder court(Court court) { this.court = court; return this; }
        public Builder decisionDate(LocalDate date) { this.decisionDate = date; return this; }
        public Builder headnotes(String notes) { this.headnotes = notes; return this; }
        public Builder legalIssues(List<String> issues) { this.legalIssues = issues; return this; }
        public Builder outcome(CaseOutcome outcome) { this.outcome = outcome; return this; }
        public Builder appliedLaws(List<LegalReference> laws) { this.appliedLaws = laws; return this; }
        public Builder parties(String parties) { this.parties = parties; return this; }

        public CourtCase build() {
            return new CourtCase(this);
        }
    }
}
