# Austrian Law Search - Work Journal

## Project Overview
A Java 21/25 application that crawls Austrian law databases, creates an indexed database, and provides intelligent legal search with context-aware responses - essentially a "pocket lawyer" for Austrian law.

## Current Status
**Phase**: Functional MVP - Crawler and Search Working
**Last Updated**: 2025-12-06

---

## Work Log

### Session 1 - 2025-12-06
- [x] Created CLAUDE.md work journal
- [x] Explored web-search-pro multiagent protocol
- [x] Designed system architecture
- [x] Set up Java 21 project structure with Maven
- [x] Created core domain models (LegalDocument, Law, CourtCase, Decree)
- [x] Implemented RIS API crawler for Austrian law databases
- [x] Created Lucene-based search index with German analyzer
- [x] Implemented German/English query parser
- [x] Built context-aware response generator with police text feature
- [x] Created CLI interface with progress tracking
- [x] Fixed compilation errors and built fat JAR
- [x] Pushed to GitHub: nebulai13/law-search and qwitch13/law-search
- [x] Fixed duplicate stop words bug in QueryParser (duplicate "an" and "in")

### Session 2 - 2025-12-06 (Continued)
- [x] Fixed SSL certificate handling for RIS API (PKIX path building issue)
- [x] Fixed RIS API parameter format (DokumenteProSeite enum values)
- [x] Fixed JSON parsing paths for RIS API responses:
  - ID: `Data.Metadaten.Technisch.ID`
  - Title: `Data.Metadaten.Bundesrecht.Titel`
  - Judikatur: `Data.Metadaten.Judikatur`
- [x] Fixed search to query multiple fields (title, fullText, abbreviation)
- [x] Added maxPages parameter for controlled crawling
- [x] Added --max-pages CLI option for testing
- [x] Verified crawler successfully indexes 100 documents per page
- [x] Verified search returns relevant results with highlighting
- [x] Note: VfGH endpoint returns 404 (may need different API pattern)

---

## Architecture Overview

### Core Components
1. **Law Crawler Module** - Crawls Austrian law databases (RIS, etc.)
2. **Database & Index Module** - Stores and indexes legal content
3. **Search Engine Module** - Semantic search in German/English
4. **Context Generator Module** - AI-powered response generation
5. **Multiagent Coordinator** - Orchestrates specialized agents

### Austrian Law Sources
- **RIS (Rechtsinformationssystem)**: https://www.ris.bka.gv.at/
  - Bundesrecht (Federal Law)
  - Landesrecht (State Law)
  - Judikatur (Case Law/Rulings)
  - Erlässe (Decrees)
- **EUR-Lex**: EU law applicable in Austria
- **Verfassungsgerichtshof**: Constitutional Court rulings
- **OGH (Oberster Gerichtshof)**: Supreme Court decisions

### Data Types to Crawl
1. **Gesetze (Laws)**: Full law texts with paragraphs
2. **Verordnungen (Regulations)**: Administrative regulations
3. **Judikatur (Case Law)**: Court decisions and rulings
4. **Erlässe (Decrees)**: Official interpretations

### Search Capabilities
- Natural language queries in German and English
- Legal reference lookup (e.g., "§ 14 VersG")
- Contextual search (e.g., "demonstration rights")
- Cross-reference detection

### Output Generation
- Relevant law excerpts with citations
- Related case law and rulings
- Plain language explanations
- Official text for authorities (police, officials)

---

## Technology Stack
- **Language**: Java 21/25
- **Build**: Maven/Gradle
- **Database**: SQLite or H2 (embedded) + Lucene for indexing
- **HTTP Client**: Java HttpClient
- **HTML Parsing**: Jsoup
- **Search**: Apache Lucene
- **NLP**: OpenNLP or similar for German text processing

---

## Multiagent Protocol (Adapted from web-search-pro)

Based on web-search-pro's architecture, we'll implement:

### Core Patterns
1. **Journaling System** - Track all operations for resumability
2. **Search Engine Manager** - Orchestrate multiple data sources
3. **Query Parser** - Advanced syntax parsing (German/English)
4. **Progress Tracker** - Real-time status updates
5. **Result Aggregator** - Merge and deduplicate results

### Java Implementation
```
at.law.search/
├── LawSearchApplication.java      # Main entry point
├── crawler/
│   ├── RisCrawler.java           # RIS OGD API client
│   ├── BundesrechtCrawler.java   # Federal law crawler
│   ├── JudikaturCrawler.java     # Case law crawler
│   └── CrawlerManager.java       # Orchestrates all crawlers
├── model/
│   ├── LegalDocument.java        # Base document type
│   ├── Law.java                  # Gesetz/Verordnung
│   ├── CourtCase.java            # Judikatur
│   ├── LegalReference.java       # § citations
│   └── SearchResult.java         # Query result
├── index/
│   ├── LuceneIndexer.java        # Full-text indexing
│   ├── DocumentStore.java        # SQLite storage
│   └── IndexManager.java         # Index lifecycle
├── search/
│   ├── QueryParser.java          # Parse German/English queries
│   ├── SearchEngine.java         # Execute searches
│   ├── RelevanceScorer.java      # Rank results
│   └── TranslationService.java   # EN<->DE translation
├── response/
│   ├── ContextGenerator.java     # Create contextual responses
│   ├── LegalSummarizer.java      # Summarize law texts
│   ├── PoliceTextGenerator.java  # Generate authority texts
│   └── CitationFormatter.java    # Format legal citations
├── journal/
│   ├── Journal.java              # Operation logging
│   ├── SessionManager.java       # Session state
│   └── JournalEntry.java         # Log entry model
└── cli/
    ├── CommandLineInterface.java # Terminal UI
    ├── ProgressTracker.java      # Progress display
    └── ResultRenderer.java       # Format output
```

### RIS OGD API v2.6 Endpoints
- Base URL: `https://data.bka.gv.at/ris/api/v2.6/`
- Bundesrecht: `GET /Bundesrecht`
- Landesrecht: `GET /Landesrecht`
- Judikatur: `GET /Judikatur`
- VfGH: `GET /Vfgh` (Constitutional Court)
- VwGH: `GET /Vwgh` (Administrative Court)
- OGH: `GET /Justiz` (Supreme Court)

---

## Next Steps
1. ~~Complete web-search-pro exploration~~ DONE
2. ~~Finalize architecture based on multiagent protocol~~ DONE
3. Create Maven project structure
4. Implement domain models
5. Implement RIS API crawler
6. Set up Lucene index + SQLite storage
7. Build query parser
8. Create CLI interface

---

## Key Legal Knowledge

### Versammlungsgesetz (Assembly Law) - Example Reference
- **§ 2 VersG**: Definition of assemblies
- **§ 2a VersG**: Spontaneous assemblies (Spontanversammlungen)
- **§ 14 VersG**: Police authority to dissolve
- Requirements: Political purpose, 3+ people, visible flags/signs
- Police must provide reasons for dissolution

---

## Notes
- Austrian law is in German; translations needed for English queries
- RIS has structured XML data available
- Consider rate limiting for crawlers
- Legal texts have specific citation formats
