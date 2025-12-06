package at.law.search.crawler;

import at.law.search.journal.Journal;
import at.law.search.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
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
    private final int maxPages;

    public RisCrawler(Journal journal) {
        this(journal, 500, Integer.MAX_VALUE); // 500ms delay between requests, unlimited pages
    }

    public RisCrawler(Journal journal, int requestDelayMs, int maxPages) {
        this.journal = journal;
        this.requestDelayMs = requestDelayMs;
        this.maxPages = maxPages;
        this.httpClient = createHttpClient();
        this.jsonMapper = new ObjectMapper();
        this.xmlMapper = new XmlMapper();
        this.executor = Executors.newFixedThreadPool(4);
    }

    /**
     * Create HttpClient with SSL context that accepts the RIS certificates.
     */
    private HttpClient createHttpClient() {
        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .sslContext(sslContext)
                .build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.warn("Could not create custom SSL context, using default: {}", e.getMessage());
            return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        }
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
     * Page size values accepted by RIS API.
     */
    private static final String PAGE_SIZE_TWENTY = "Twenty";
    private static final String PAGE_SIZE_FIFTY = "Fifty";
    private static final String PAGE_SIZE_HUNDRED = "OneHundred";

    /**
     * Search for documents in a specific application.
     */
    public List<LegalDocument> search(Application app, String searchTerm, int maxResults) {
        journal.info("Starting search in " + app.getDescription() + " for: " + searchTerm);

        try {
            String pageSize = maxResults <= 20 ? PAGE_SIZE_TWENTY :
                              maxResults <= 50 ? PAGE_SIZE_FIFTY : PAGE_SIZE_HUNDRED;

            String url = String.format("%s/%s?Suchworte=%s&DokumenteProSeite=%s",
                BASE_URL,
                app.getApiName(),
                java.net.URLEncoder.encode(searchTerm, "UTF-8"),
                pageSize);

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
                journal.logError("RIS API", new IOException("HTTP " + response.statusCode() + ": " + response.body()));
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
        int totalProcessed = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                String url = String.format("%s/%s?Seitennummer=%d&DokumenteProSeite=%s",
                    BASE_URL, app.getApiName(), page, PAGE_SIZE_HUNDRED);

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

                    hasMore = docs.size() >= 100 && page < maxPages; // OneHundred page size
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

            log.debug("Parse: OgdDocumentReference isArray={} size={}", results.isArray(),
                results.isArray() ? results.size() : 0);

            if (results.isArray()) {
                for (JsonNode docRef : results) {
                    LegalDocument doc = parseDocument(app, docRef);
                    if (doc != null) {
                        documents.add(doc);
                    } else {
                        // Debug: show first few nodes that fail to parse
                        if (documents.size() < 3) {
                            log.debug("Failed to parse node. Data keys: {}",
                                docRef.path("Data").fieldNames().hasNext() ?
                                iteratorToString(docRef.path("Data").fieldNames()) : "none");
                        }
                    }
                }
            } else if (!results.isMissingNode()) {
                // Single result case
                LegalDocument doc = parseDocument(app, results);
                if (doc != null) {
                    documents.add(doc);
                }
            }

            log.debug("Parsed {} documents from {} response", documents.size(), app.getApiName());
        } catch (Exception e) {
            journal.logError("Parse results", e);
        }

        return documents;
    }

    private String iteratorToString(Iterator<String> iter) {
        StringBuilder sb = new StringBuilder();
        while (iter.hasNext()) {
            sb.append(iter.next()).append(", ");
        }
        return sb.toString();
    }

    /**
     * Parse a single document from JSON node.
     */
    private LegalDocument parseDocument(Application app, JsonNode node) {
        try {
            JsonNode data = node.path("Data");
            JsonNode metadata = data.path("Metadaten");
            JsonNode technisch = metadata.path("Technisch");

            // ID is at Data.Metadaten.Technisch.ID
            String id = getTextOrNull(technisch, "ID");
            if (id == null) {
                id = getTextOrNull(data, "Dokumentnummer");
            }

            // Determine document type based on application and get title
            return switch (app) {
                case BUNDESRECHT, LANDESRECHT, BUNDESNORMEN -> {
                    // Bundesrecht is at Data.Metadaten.Bundesrecht (NOT Data.Bundesrecht!)
                    JsonNode bundesrecht = metadata.path("Bundesrecht");
                    String title = getTextOrNull(bundesrecht, "Titel");
                    if (title == null) title = getTextOrNull(bundesrecht, "Kurztitel");
                    if (id == null || title == null) yield null;
                    yield parseLaw(id, title, null, bundesrecht, metadata);
                }
                case VFGH, VWGH, JUSTIZ, BVWG, LVWG -> {
                    // Judikatur is at Data.Metadaten.Judikatur
                    JsonNode judikatur = metadata.path("Judikatur");
                    String title = getTextOrNull(judikatur, "Dokumenttyp");
                    String caseNum = getFirstArrayItem(judikatur.path("Geschaeftszahl"), "item");
                    if (title == null && caseNum != null) title = caseNum;
                    if (id == null || title == null) yield null;
                    yield parseCourtCase(app, id, title, null, judikatur, metadata);
                }
                default -> null;
            };
        } catch (Exception e) {
            log.debug("Failed to parse document: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get first item from a JSON array.
     */
    private String getFirstArrayItem(JsonNode array, String fieldName) {
        JsonNode items = array.path(fieldName);
        if (items.isArray() && items.size() > 0) {
            return items.get(0).asText();
        }
        if (items.isTextual()) {
            return items.asText();
        }
        return null;
    }

    /**
     * Parse a law document.
     */
    private Law parseLaw(String id, String title, String fullText, JsonNode bundesrecht, JsonNode metadata) {
        // Abbreviation is at Data.Bundesrecht.Kurztitel
        String abbreviation = getTextOrNull(bundesrecht, "Kurztitel");
        if (abbreviation == null) {
            abbreviation = getTextOrNull(bundesrecht, "Abkuerzung");
        }

        // Publication info
        String bgblNumber = getTextOrNull(bundesrecht, "Kundmachungsorgan");
        if (bgblNumber == null) {
            bgblNumber = getTextOrNull(metadata, "Kundmachungsorgan");
        }

        // Effective date
        String dateStr = getTextOrNull(bundesrecht, "Inkrafttretedatum");
        if (dateStr == null) {
            dateStr = getTextOrNull(metadata, "Inkrafttretedatum");
        }

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
    private CourtCase parseCourtCase(Application app, String id, String title, String fullText, JsonNode judikatur, JsonNode metadata) {
        // Case number from Judikatur.Geschaeftszahl.item array
        String caseNumber = getFirstArrayItem(judikatur.path("Geschaeftszahl"), "item");
        if (caseNumber == null) {
            caseNumber = getTextOrNull(judikatur, "Geschaeftszahl");
        }

        // Decision date
        String dateStr = getTextOrNull(judikatur, "Entscheidungsdatum");

        // Rechtssatz (headnotes) - may be in different locations
        String headnotes = getTextOrNull(judikatur, "Rechtssatz");
        if (headnotes == null) {
            headnotes = getTextOrNull(judikatur, "Dokumenttyp");
        }

        // Try to detect court from Judikatur.Justiz.Gericht
        JsonNode justiz = judikatur.path("Justiz");
        String gerichtStr = getTextOrNull(justiz, "Gericht");

        CourtCase.Court court = switch (app) {
            case VFGH -> CourtCase.Court.VFGH;
            case VWGH -> CourtCase.Court.VWGH;
            case JUSTIZ -> {
                if (gerichtStr != null && gerichtStr.contains("OGH")) {
                    yield CourtCase.Court.OGH;
                }
                yield CourtCase.Court.OGH;
            }
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
