package at.law.search.index;

import at.law.search.model.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Lucene-based indexer for legal documents.
 * Provides full-text search with German language analysis.
 */
public class LuceneIndexer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexer.class);

    // Field names
    public static final String FIELD_ID = "id";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_FULL_TEXT = "fullText";
    public static final String FIELD_CITATION = "citation";
    public static final String FIELD_DOC_TYPE = "docType";
    public static final String FIELD_ABBREVIATION = "abbreviation";
    public static final String FIELD_CASE_NUMBER = "caseNumber";
    public static final String FIELD_COURT = "court";
    public static final String FIELD_EFFECTIVE_DATE = "effectiveDate";
    public static final String FIELD_REFERENCES = "references";

    private final Directory directory;
    private final Analyzer analyzer;
    private IndexWriter writer;
    private DirectoryReader reader;
    private IndexSearcher searcher;

    public LuceneIndexer(Path indexPath) throws IOException {
        Files.createDirectories(indexPath);
        this.directory = FSDirectory.open(indexPath);

        // Use German analyzer for text fields, standard for identifiers
        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
        fieldAnalyzers.put(FIELD_ID, new StandardAnalyzer());
        fieldAnalyzers.put(FIELD_ABBREVIATION, new StandardAnalyzer());
        fieldAnalyzers.put(FIELD_CASE_NUMBER, new StandardAnalyzer());

        this.analyzer = new PerFieldAnalyzerWrapper(new GermanAnalyzer(), fieldAnalyzers);

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.writer = new IndexWriter(directory, config);
    }

    /**
     * Index a legal document.
     */
    public void indexDocument(LegalDocument doc) throws IOException {
        Document luceneDoc = new Document();

        // Common fields
        luceneDoc.add(new StringField(FIELD_ID, doc.getId(), Field.Store.YES));
        luceneDoc.add(new TextField(FIELD_TITLE, doc.getTitle(), Field.Store.YES));
        luceneDoc.add(new StringField(FIELD_DOC_TYPE, doc.getDocumentType().name(), Field.Store.YES));
        luceneDoc.add(new StoredField(FIELD_CITATION, doc.getCitation()));

        if (doc.getFullText() != null) {
            luceneDoc.add(new TextField(FIELD_FULL_TEXT, doc.getFullText(), Field.Store.NO));
        }

        if (doc.getEffectiveDate() != null) {
            luceneDoc.add(new LongPoint(FIELD_EFFECTIVE_DATE, doc.getEffectiveDate().toEpochDay()));
        }

        // Index references
        for (LegalReference ref : doc.getReferences()) {
            luceneDoc.add(new TextField(FIELD_REFERENCES, ref.toCitation(), Field.Store.YES));
        }

        // Type-specific fields
        switch (doc) {
            case Law law -> {
                if (law.getAbbreviation() != null) {
                    luceneDoc.add(new StringField(FIELD_ABBREVIATION, law.getAbbreviation(), Field.Store.YES));
                    luceneDoc.add(new TextField(FIELD_ABBREVIATION + "_text", law.getAbbreviation(), Field.Store.NO));
                }
            }
            case CourtCase courtCase -> {
                luceneDoc.add(new StringField(FIELD_CASE_NUMBER, courtCase.getCaseNumber(), Field.Store.YES));
                luceneDoc.add(new StringField(FIELD_COURT, courtCase.getCourt().name(), Field.Store.YES));
                if (courtCase.getHeadnotes() != null) {
                    luceneDoc.add(new TextField("headnotes", courtCase.getHeadnotes(), Field.Store.YES));
                }
            }
            case Decree decree -> {
                if (decree.getIssuingAuthority() != null) {
                    luceneDoc.add(new TextField("authority", decree.getIssuingAuthority(), Field.Store.YES));
                }
            }
        }

        // Update existing or add new
        writer.updateDocument(new Term(FIELD_ID, doc.getId()), luceneDoc);
        log.debug("Indexed document: {}", doc.getId());
    }

    /**
     * Commit pending changes.
     */
    public void commit() throws IOException {
        writer.commit();
        refreshReader();
    }

    /**
     * Search for documents.
     */
    public List<SearchResult> search(String queryString, int maxResults) throws IOException, ParseException {
        refreshReader();

        QueryParser parser = new QueryParser(FIELD_FULL_TEXT, analyzer);
        parser.setDefaultOperator(QueryParser.Operator.AND);
        Query query = parser.parse(queryString);

        return executeSearch(query, maxResults, queryString);
    }

    /**
     * Search by legal reference (e.g., "§ 14 VersG").
     */
    public List<SearchResult> searchByReference(LegalReference ref, int maxResults) throws IOException {
        refreshReader();

        BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();

        if (ref.lawAbbreviation() != null) {
            boolQuery.add(new TermQuery(new Term(FIELD_ABBREVIATION, ref.lawAbbreviation())),
                BooleanClause.Occur.SHOULD);
        }

        // Also search in full text for the citation
        try {
            QueryParser parser = new QueryParser(FIELD_FULL_TEXT, analyzer);
            boolQuery.add(parser.parse("\"" + ref.toCitation() + "\""), BooleanClause.Occur.SHOULD);
        } catch (ParseException e) {
            log.debug("Could not parse reference query: {}", e.getMessage());
        }

        return executeSearch(boolQuery.build(), maxResults, ref.toCitation());
    }

    /**
     * Search for laws by abbreviation.
     */
    public List<SearchResult> searchByAbbreviation(String abbreviation, int maxResults) throws IOException {
        refreshReader();

        Query query = new TermQuery(new Term(FIELD_ABBREVIATION, abbreviation.toUpperCase()));
        return executeSearch(query, maxResults, abbreviation);
    }

    /**
     * Search court cases by case number.
     */
    public List<SearchResult> searchByCaseNumber(String caseNumber, int maxResults) throws IOException {
        refreshReader();

        Query query = new WildcardQuery(new Term(FIELD_CASE_NUMBER, "*" + caseNumber + "*"));
        return executeSearch(query, maxResults, caseNumber);
    }

    /**
     * Advanced search with multiple criteria.
     */
    public List<SearchResult> advancedSearch(String text, LegalDocument.DocumentType docType,
                                             String lawAbbreviation, int maxResults) throws IOException, ParseException {
        refreshReader();

        BooleanQuery.Builder boolQuery = new BooleanQuery.Builder();

        if (text != null && !text.isBlank()) {
            QueryParser parser = new QueryParser(FIELD_FULL_TEXT, analyzer);
            boolQuery.add(parser.parse(text), BooleanClause.Occur.MUST);
        }

        if (docType != null) {
            boolQuery.add(new TermQuery(new Term(FIELD_DOC_TYPE, docType.name())),
                BooleanClause.Occur.MUST);
        }

        if (lawAbbreviation != null) {
            boolQuery.add(new TermQuery(new Term(FIELD_ABBREVIATION, lawAbbreviation)),
                BooleanClause.Occur.MUST);
        }

        return executeSearch(boolQuery.build(), maxResults, text);
    }

    /**
     * Execute search and create results with highlights.
     */
    private List<SearchResult> executeSearch(Query query, int maxResults, String originalQuery) throws IOException {
        TopDocs topDocs = searcher.search(query, maxResults);
        List<SearchResult> results = new ArrayList<>();

        // Setup highlighter
        QueryScorer scorer = new QueryScorer(query);
        Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter("<b>", "</b>"), scorer);
        highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, 150));

        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(scoreDoc.doc);

            // Create a placeholder LegalDocument for the result
            String id = doc.get(FIELD_ID);
            String title = doc.get(FIELD_TITLE);
            String citation = doc.get(FIELD_CITATION);
            String docTypeStr = doc.get(FIELD_DOC_TYPE);

            LegalDocument.DocumentType docType = LegalDocument.DocumentType.valueOf(docTypeStr);

            // Get highlights
            List<String> highlights = new ArrayList<>();
            try {
                String[] fragments = highlighter.getBestFragments(
                    analyzer, FIELD_TITLE, title, 2);
                highlights.addAll(Arrays.asList(fragments));
            } catch (Exception e) {
                log.debug("Highlighting failed: {}", e.getMessage());
            }

            // Create a simple Law placeholder for results
            Law lawPlaceholder = Law.builder()
                .id(id)
                .title(title)
                .documentType(docType)
                .build();

            results.add(new SearchResult(
                lawPlaceholder,
                scoreDoc.score,
                highlights,
                List.of(),
                SearchResult.MatchType.KEYWORD_MATCH
            ));
        }

        return results;
    }

    /**
     * Get document count.
     */
    public int getDocumentCount() throws IOException {
        refreshReader();
        return reader.numDocs();
    }

    private void refreshReader() throws IOException {
        if (reader == null) {
            reader = DirectoryReader.open(directory);
            searcher = new IndexSearcher(reader);
        } else {
            DirectoryReader newReader = DirectoryReader.openIfChanged(reader);
            if (newReader != null) {
                reader.close();
                reader = newReader;
                searcher = new IndexSearcher(reader);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
        if (reader != null) {
            reader.close();
        }
        if (directory != null) {
            directory.close();
        }
    }
}
