package at.law.search.crawler;

import at.law.search.journal.Journal;
import at.law.search.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Crawler for the Austrian RIS (Rechtsinformationssystem) OGD API.
 * Fetches laws, regulations, and court decisions from the official API.
 *
 * API Documentation: https://data.bka.gv.at/ris/ogd/v2.6/Documents/Dokumentation_OGD-RIS_API.pdf
 */
public class RisCrawler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RisCrawler.class);
    private static final String BASE_URL = "https://data.bka.gv.at/ris/api/v2.6";

    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;
    private final XmlMapper xmlMapper;
    private final Journal journal;
    private final ExecutorService executor;
    private final int requestDelayMs;

    public RisCrawler(Journal journal) {
        this(journal, 500); // 500ms delay between requests
    }

    public RisCrawler(Journal journal, int requestDelayMs) {
        this.journal = journal;
        this.requestDelayMs = requestDelayMs;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.jsonMapper = new ObjectMapper();
        this.xmlMapper = new XmlMapper();
        this.executor = Executors.newFixedThreadPool(4);
    }

    /**
     * Application types available in RIS OGD API.
     */
    public enum Application {
        BUNDESRECHT("Bundesrecht", "Federal Law"),
        LANDESRECHT("Landesrecht", "State Law"),
        BUNDESNORMEN("Bundesnormen", "Federal Norms"),
        VFGH("Vfgh", "Constitutional Court"),
        VWGH("Vwgh", "Administrative Court"),
        JUSTIZ("Justiz", "Justice/OGH"),
        BVWG("Bvwg", "Federal Administrative Court"),
        LVWG("Lvwg", "State Administrative Courts"),
        BGBL_AUTH("BgblAuth", "Federal Law Gazette Authentic");

        private final String apiName;
        private final String description;

        Application(String apiName, String description) {
            this.apiName = apiName;
            this.description = description;
        }

        public String getApiName() { return apiName; }
        public String getDescription() { return description; }
    }

    /**
     * Search for documents in a specific application.
     */
    public List<LegalDocument> search(Application app, String searchTerm, int maxResults) {
        journal.info("Starting search in " + app.getDescription() + " for: " + searchTerm);

        try {
            String url = String.format("%s/%s?Suchworte=%s&DoksProSeite=%d",
                BASE_URL,
                app.getApiName(),
                java.net.URLEncoder.encode(searchTerm, "UTF-8"),
                Math.min(maxResults, 100));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseSearchResults(app, response.body());
            } else {
                journal.logError("RIS API", new IOException("HTTP " + response.statusCode()));
                return List.of();
            }
        } catch (Exception e) {
            journal.logError("RIS search", e);
            return List.of();
        }
    }

    /**
     * Crawl all documents from a specific application with pagination.
     */
    public void crawlAll(Application app, Consumer<LegalDocument> documentHandler, Consumer<CrawlProgress> progressHandler) {
        journal.info("Starting full crawl of " + app.getDescription());

        int page = 1;
        int pageSize = 100;
        int totalProcessed = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                String url = String.format("%s/%s?Seite=%d&DokumenteProSeite=%d",
                    BASE_URL, app.getApiName(), page, pageSize);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    List<LegalDocument> docs = parseSearchResults(app, response.body());

                    for (LegalDocument doc : docs) {
                        documentHandler.accept(doc);
                        totalProcessed++;

                        if (totalProcessed % 10 == 0) {
                            progressHandler.accept(new CrawlProgress(
                                app.getApiName(), totalProcessed, -1, doc.getTitle()
                            ));
                        }
                    }

                    hasMore = docs.size() >= pageSize;
                    page++;

                    // Rate limiting
                    Thread.sleep(requestDelayMs);
                } else {
                    log.warn("HTTP {} for page {}", response.statusCode(), page);
                    hasMore = false;
                }
            } catch (Exception e) {
                journal.logError("Crawl " + app.getApiName(), e);
                hasMore = false;
            }
        }

        journal.info("Crawl complete: " + totalProcessed + " documents from " + app.getDescription());
    }

    /**
     * Fetch a specific document by its ID.
     */
    public Optional<LegalDocument> fetchDocument(Application app, String documentId) {
        try {
            String url = String.format("%s/%s/%s",
                BASE_URL, app.getApiName(), documentId);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<LegalDocument> results = parseSearchResults(app, response.body());
                return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
            }
        } catch (Exception e) {
            journal.logError("Fetch document " + documentId, e);
        }
        return Optional.empty();
    }

    /**
     * Parse search results from JSON response.
     */
    private List<LegalDocument> parseSearchResults(Application app, String jsonResponse) {
        List<LegalDocument> documents = new ArrayList<>();

        try {
            JsonNode root = jsonMapper.readTree(jsonResponse);
            JsonNode results = root.path("OgdSearchResult").path("OgdDocumentResults").path("OgdDocumentReference");

            if (results.isArray()) {
                for (JsonNode docRef : results) {
                    LegalDocument doc = parseDocument(app, docRef);
                    if (doc != null) {
                        documents.add(doc);
                    }
                }
            }
        } catch (Exception e) {
            journal.logError("Parse results", e);
        }

        return documents;
    }

    /**
     * Parse a single document from JSON node.
     */
    private LegalDocument parseDocument(Application app, JsonNode node) {
        try {
            JsonNode data = node.path("Data");
            JsonNode metadata = data.path("Metadaten");

            String id = getTextOrNull(data, "Dokumentnummer");
            String title = getTextOrNull(metadata, "Titel");
            String fullText = getTextOrNull(data, "Dokumentinhalt");

            if (id == null || title == null) {
                return null;
            }

            // Determine document type based on application
            return switch (app) {
                case BUNDESRECHT, LANDESRECHT, BUNDESNORMEN -> parseLaw(id, title, fullText, metadata);
                case VFGH, VWGH, JUSTIZ, BVWG, LVWG -> parseCourtCase(app, id, title, fullText, metadata);
                default -> null;
            };
        } catch (Exception e) {
            log.debug("Failed to parse document: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse a law document.
     */
    private Law parseLaw(String id, String title, String fullText, JsonNode metadata) {
        String abbreviation = getTextOrNull(metadata, "Abkuerzung");
        String bgblNumber = getTextOrNull(metadata, "Kundmachungsorgan");
        String dateStr = getTextOrNull(metadata, "Inkrafttretedatum");

        LocalDate effectiveDate = null;
        if (dateStr != null) {
            try {
                effectiveDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ignored) {}
        }

        return Law.builder()
            .id(id)
            .title(title)
            .fullText(fullText)
            .abbreviation(abbreviation)
            .bgblNumber(bgblNumber)
            .effectiveDate(effectiveDate)
            .documentType(LegalDocument.DocumentType.BUNDESGESETZ)
            .sourceUrl(BASE_URL + "/Bundesrecht/" + id)
            .build();
    }

    /**
     * Parse a court case document.
     */
    private CourtCase parseCourtCase(Application app, String id, String title, String fullText, JsonNode metadata) {
        String caseNumber = getTextOrNull(metadata, "Geschaeftszahl");
        String dateStr = getTextOrNull(metadata, "Entscheidungsdatum");
        String headnotes = getTextOrNull(metadata, "Rechtssatz");

        CourtCase.Court court = switch (app) {
            case VFGH -> CourtCase.Court.VFGH;
            case VWGH -> CourtCase.Court.VWGH;
            case JUSTIZ -> CourtCase.Court.OGH;
            case BVWG -> CourtCase.Court.BVWG;
            case LVWG -> CourtCase.Court.LVG;
            default -> CourtCase.Court.OGH;
        };

        LocalDate decisionDate = null;
        if (dateStr != null) {
            try {
                decisionDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ignored) {}
        }

        LegalDocument.DocumentType docType = switch (court) {
            case VFGH -> LegalDocument.DocumentType.JUDIKATUR_VFGH;
            case VWGH -> LegalDocument.DocumentType.JUDIKATUR_VWGH;
            case OGH -> LegalDocument.DocumentType.JUDIKATUR_OGH;
            default -> LegalDocument.DocumentType.JUDIKATUR_BVWG;
        };

        return CourtCase.builder()
            .id(id)
            .title(title)
            .fullText(fullText)
            .caseNumber(caseNumber != null ? caseNumber : id)
            .court(court)
            .decisionDate(decisionDate)
            .headnotes(headnotes)
            .documentType(docType)
            .sourceUrl(BASE_URL + "/" + app.getApiName() + "/" + id)
            .build();
    }

    private String getTextOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        return field.isMissingNode() || field.isNull() ? null : field.asText();
    }

    /**
     * Progress information during crawling.
     */
    public record CrawlProgress(String source, int processed, int total, String currentItem) {}

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
