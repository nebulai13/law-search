# Austrian Law Search (Pocket Lawyer)

A Java 21 command-line application that crawls Austrian law databases (RIS - Rechtsinformationssystem), creates an indexed full-text search database, and provides intelligent legal search with context-aware responses in German and English.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Usage](#usage)
- [Architecture](#architecture)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

---

## Features

### Core Functionality
- **RIS Database Crawler**: Crawls the official Austrian RIS OGD API v2.6 for federal laws, regulations, and court decisions
- **Full-Text Search**: Powered by Apache Lucene 9.9 with German language analysis (stemming, stop words, compound splitting)
- **Natural Language Queries**: Search in German or English with automatic language detection
- **Legal Reference Detection**: Automatically detects and parses citations like `§ 14 VersG`, `Art. 10 B-VG`
- **Relevance Scoring**: Results ranked by relevance with highlighted matching terms
- **Context-Aware Responses**: Generates comprehensive legal summaries
- **Police/Authority Text**: Creates official text with legal references for authorities
- **Session Journaling**: Tracks all operations for debugging and session resumability

### Data Sources

| Source | API Name | Description |
|--------|----------|-------------|
| **Bundesrecht** | `Bundesrecht` | Federal Laws and Regulations |
| **Landesrecht** | `Landesrecht` | State/Provincial Laws |
| **Bundesnormen** | `Bundesnormen` | Federal Norms |
| **VfGH** | `Vfgh` | Constitutional Court Decisions |
| **VwGH** | `Vwgh` | Administrative Court Decisions |
| **OGH/Justiz** | `Justiz` | Supreme Court Decisions |
| **BVwG** | `Bvwg` | Federal Administrative Court |
| **LVwG** | `Lvwg` | State Administrative Courts |

---

## Requirements

- **Java 21** or higher (tested with Java 22/25)
- **Maven 3.8+** for building
- **Internet connection** for crawling the RIS API
- **~500MB disk space** for full index

### Verify Java Version
```bash
java -version
# Should show: openjdk version "21" or higher
```

---

## Installation

### Option 1: Build from Source

```bash
# Clone the repository
git clone https://github.com/nebulai13/law-search.git
cd law-search

# Build the project (creates fat JAR with all dependencies)
mvn package -DskipTests

# Verify the build
ls -la target/law-search-1.0.0-SNAPSHOT.jar
```

### Option 2: Download Release
```bash
# Download the latest release JAR
curl -LO https://github.com/nebulai13/law-search/releases/latest/download/law-search-1.0.0-SNAPSHOT.jar
```

---

## Quick Start

```bash
# 1. Crawl the RIS database (fetches 100 documents per page)
java -jar target/law-search-1.0.0-SNAPSHOT.jar --crawl

# 2. Search for laws
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "Verordnung"

# 3. Combined: Crawl and search in one command
java -jar target/law-search-1.0.0-SNAPSHOT.jar --crawl -q "bundesgesetz"
```

### Example Output
```
╔════════════════════════════════════════════════════════╗
║      Austrian Law Search - Pocket Lawyer               ║
║      Rechtsinformationssystem Suche                    ║
╚════════════════════════════════════════════════════════╝

Session ID: 20251206_232228_f5c263
Data directory: ./data
Indexed documents: 200

Searching: Verordnung
──────────────────────────────────────────────────
Found 20 results in 23ms

[1] 2.11 - Verordnung des Vorstands der E-Control...
    Bundesgesetz
    ...<b>Verordnung</b> des Vorstands der E-Control...

[2] 2.00 - Verordnung der Finanzmarktaufsichtsbehörde...
    Bundesgesetz
    ...
```

---

## Usage

### Command-Line Options

```bash
java -jar target/law-search-1.0.0-SNAPSHOT.jar [OPTIONS]
```

| Option | Short | Description | Default |
|--------|-------|-------------|---------|
| `--help` | `-h` | Show help message | - |
| `--version` | `-V` | Show version | - |
| `--query` | `-q` | Search query | - |
| `--crawl` | `-c` | Crawl RIS database before searching | false |
| `--interactive` | `-i` | Interactive mode | false |
| `--data-dir` | `-d` | Data directory path | `./data` |
| `--max-results` | `-m` | Maximum search results | 20 |
| `--max-pages` | - | Max pages to crawl per source | 1 |
| `--type` | `-t` | Filter by document type | - |
| `--police-text` | - | Generate police/authority text | false |

### Search Examples

```bash
# Basic keyword search (German)
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "Versammlungsfreiheit"

# Search with legal reference
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "§ 14 VersG"

# Search in English (auto-translated to German)
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "freedom of assembly"

# Exclude terms
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "polizei -gewalt"

# Required terms
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "+spontan +versammlung"

# Exact phrase
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q '"Versammlungsgesetz"'

# Generate police text
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "demonstration" --police-text

# Extended crawl (5 pages = 500 documents)
java -jar target/law-search-1.0.0-SNAPSHOT.jar --crawl --max-pages 5 -q "bundesgesetz"

# Filter by document type
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "verfassung" -t BUNDESGESETZ
```

### Interactive Mode

```bash
java -jar target/law-search-1.0.0-SNAPSHOT.jar --interactive
```

**Commands:**
| Command | Description |
|---------|-------------|
| `/help` | Show help |
| `/crawl` | Crawl RIS database |
| `/stats` | Show index statistics |
| `/police` | Toggle police text output |
| `/quit` | Exit application |

### Search Syntax

| Syntax | Example | Description |
|--------|---------|-------------|
| `keyword` | `verordnung` | Basic search |
| `+term` | `+spontan +demo` | Required term (AND) |
| `-term` | `polizei -gewalt` | Exclude term (NOT) |
| `"phrase"` | `"Versammlungsgesetz"` | Exact phrase match |
| `§ N Law` | `§ 14 VersG` | Legal paragraph reference |
| `Art. N Law` | `Art. 10 B-VG` | Article reference |

---

## Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Command Line Interface                    │
│                  (CommandLineInterface.java)                 │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   RIS Crawler   │  │  Search Engine  │  │    Response     │
│ (RisCrawler.java)│ │(SearchEngine.java)│ │   Generator     │
└─────────────────┘  └─────────────────┘  └─────────────────┘
         │                    │                    │
         ▼                    ▼                    │
┌─────────────────────────────────────────┐       │
│            Lucene Indexer               │◄──────┘
│         (LuceneIndexer.java)            │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│           Domain Models                  │
│  Law, CourtCase, Decree, LegalReference │
└─────────────────────────────────────────┘
```

### Project Structure

```
law-search/
├── src/main/java/at/law/search/
│   ├── LawSearchApplication.java      # Main entry point
│   ├── cli/
│   │   └── CommandLineInterface.java  # CLI with Picocli
│   ├── crawler/
│   │   └── RisCrawler.java            # RIS OGD API crawler
│   ├── index/
│   │   └── LuceneIndexer.java         # Lucene indexing & search
│   ├── journal/
│   │   ├── Journal.java               # Session logging
│   │   └── JournalEntry.java          # Log entry model
│   ├── model/
│   │   ├── LegalDocument.java         # Base sealed class
│   │   ├── Law.java                   # Federal/state laws
│   │   ├── CourtCase.java             # Court decisions
│   │   ├── Decree.java                # Administrative decrees
│   │   ├── LegalReference.java        # § and Art. citations
│   │   └── SearchResult.java          # Search result wrapper
│   ├── response/
│   │   └── ResponseGenerator.java     # Context-aware responses
│   └── search/
│       ├── SearchEngine.java          # Search orchestration
│       └── QueryParser.java           # German/English parser
├── src/main/resources/
│   └── logback.xml                    # Logging configuration
├── pom.xml                            # Maven build config
├── CLAUDE.md                          # Development journal
└── README.md                          # This file
```

### Key Components

#### 1. RisCrawler (`crawler/RisCrawler.java`)
Crawls the Austrian RIS OGD API with:
- SSL certificate handling for government servers
- Rate limiting (500ms between requests)
- Pagination support (100 documents per page)
- JSON parsing for Bundesrecht and Judikatur structures

```java
// API endpoints crawled
https://data.bka.gv.at/ris/api/v2.6/Bundesrecht
https://data.bka.gv.at/ris/api/v2.6/Justiz
// etc.
```

#### 2. LuceneIndexer (`index/LuceneIndexer.java`)
Full-text indexing with:
- German language analyzer (stemming, compound words)
- Multi-field search (title, fullText, abbreviation)
- Stored fields for display
- Real-time index updates

**Indexed Fields:**
| Field | Type | Analyzed | Stored |
|-------|------|----------|--------|
| `id` | StringField | No | Yes |
| `title` | TextField | Yes (German) | Yes |
| `fullText` | TextField | Yes (German) | No |
| `docType` | StringField | No | Yes |
| `abbreviation` | StringField | No | Yes |
| `caseNumber` | StringField | No | Yes |
| `court` | StringField | No | Yes |

#### 3. QueryParser (`search/QueryParser.java`)
Natural language query parsing:
- Language detection (German/English)
- Legal reference extraction (`§ 14 VersG` → structured reference)
- Advanced syntax (`+required`, `-excluded`, `"phrase"`)
- German ↔ English translation for common legal terms

#### 4. Domain Models

```java
// Sealed class hierarchy for type safety
public sealed abstract class LegalDocument
    permits Law, CourtCase, Decree {
    // Common fields: id, title, fullText, documentType, etc.
}

public final class Law extends LegalDocument {
    private final String abbreviation;      // e.g., "VersG"
    private final String bgblNumber;        // e.g., "BGBl. I Nr. 98/2002"
    private final LocalDate effectiveDate;
}

public final class CourtCase extends LegalDocument {
    private final String caseNumber;        // e.g., "2Ob137/78"
    private final Court court;              // VFGH, VWGH, OGH, etc.
    private final LocalDate decisionDate;
    private final String headnotes;
}
```

---

## API Reference

### RIS OGD API v2.6

**Base URL:** `https://data.bka.gv.at/ris/api/v2.6/`

**Documentation:** [OGD-RIS API Handbuch (PDF)](https://data.bka.gv.at/ris/ogd/v2.6/Documents/Dokumentation_OGD-RIS_API.pdf)

#### Endpoints

| Endpoint | Description |
|----------|-------------|
| `/Bundesrecht` | Federal laws and regulations |
| `/Landesrecht` | State laws |
| `/Bundesnormen` | Federal norms |
| `/Justiz` | Supreme Court (OGH) decisions |
| `/Vfgh` | Constitutional Court decisions |
| `/Vwgh` | Administrative Court decisions |
| `/Bvwg` | Federal Administrative Court |
| `/Lvwg` | State Administrative Courts |

#### Query Parameters

| Parameter | Values | Description |
|-----------|--------|-------------|
| `Suchworte` | URL-encoded text | Search terms |
| `Seitennummer` | 1, 2, 3... | Page number |
| `DokumenteProSeite` | `Twenty`, `Fifty`, `OneHundred` | Results per page |

#### Response Structure (Bundesrecht)

```json
{
  "OgdSearchResult": {
    "OgdDocumentResults": {
      "OgdDocumentReference": [
        {
          "Data": {
            "Metadaten": {
              "Technisch": {
                "ID": "BGBLA_2025_II_277"
              },
              "Bundesrecht": {
                "Kurztitel": "COVID-19-BMV",
                "Titel": "Verordnung des Bundesministers...",
                "Eli": "https://www.ris.bka.gv.at/eli/..."
              }
            },
            "Dokumentliste": { ... }
          }
        }
      ]
    }
  }
}
```

---

## Configuration

### Data Directory
```bash
# Default: ./data
java -jar target/law-search-1.0.0-SNAPSHOT.jar -d /path/to/data --crawl
```

**Directory Structure:**
```
data/
├── index/          # Lucene index files
├── journal/        # Session logs (JSON)
└── cache/          # API response cache (future)
```

### Logging (`src/main/resources/logback.xml`)

```xml
<!-- Change log level -->
<logger name="at.law.search" level="DEBUG"/>  <!-- DEBUG, INFO, WARN -->

<!-- Log to file -->
<appender name="FILE" class="ch.qos.logback.core.FileAppender">
    <file>logs/law-search.log</file>
</appender>
```

### Environment Variables (Future)

| Variable | Description | Default |
|----------|-------------|---------|
| `LAW_SEARCH_DATA_DIR` | Data directory | `./data` |
| `LAW_SEARCH_API_DELAY` | API request delay (ms) | `500` |
| `LAW_SEARCH_MAX_PAGES` | Max pages per crawl | `1` |

---

## Troubleshooting

### Common Issues

#### 1. SSL Certificate Errors
```
PKIX path building failed: unable to find valid certification path
```
**Solution:** The application includes a trust manager for RIS certificates. If issues persist, update your Java trust store:
```bash
keytool -import -alias ris -file ris.crt -keystore $JAVA_HOME/lib/security/cacerts
```

#### 2. No Search Results
```
Searching: Bundesminister
No results found.
```
**Cause:** German analyzer applies stemming. Try lowercase or partial terms:
```bash
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "bundesminister"
# or
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "minister"
```

#### 3. VfGH Returns 404
```
HTTP 404 for page 1
```
**Cause:** Some RIS endpoints require different API patterns. Currently only Bundesrecht is fully supported.

#### 4. Out of Memory
```
java.lang.OutOfMemoryError: Java heap space
```
**Solution:** Increase heap size:
```bash
java -Xmx2g -jar target/law-search-1.0.0-SNAPSHOT.jar --crawl --max-pages 10
```

#### 5. Slow Crawling
**Cause:** Rate limiting (500ms between requests) to respect RIS API.
**Solution:** Use `--max-pages` to limit crawl scope for testing.

### Debug Mode

```bash
# Enable debug logging
java -Dlogback.configurationFile=logback-debug.xml \
     -jar target/law-search-1.0.0-SNAPSHOT.jar --crawl
```

---

## Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21+ | Runtime platform |
| Apache Lucene | 9.9.1 | Full-text search engine |
| Jackson | 2.16.1 | JSON/XML processing |
| Picocli | 4.7.5 | CLI framework |
| SLF4J + Logback | 2.0.9 / 1.4.14 | Logging |
| Jsoup | 1.17.2 | HTML parsing |
| JUnit 5 | 5.10.1 | Testing |

---

## Contributing

### Development Setup

```bash
# Clone and build
git clone https://github.com/nebulai13/law-search.git
cd law-search
mvn compile

# Run tests
mvn test

# Run with hot reload (development)
mvn compile exec:java -Dexec.mainClass="at.law.search.LawSearchApplication" \
    -Dexec.args="--crawl -q verordnung"
```

### Code Style
- Java 21 features: records, sealed classes, pattern matching
- Follow existing package structure
- Add Javadoc for public methods
- Update CLAUDE.md with changes

### Pull Request Process
1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

---

## License

MIT License - see [LICENSE](LICENSE) file.

---

## Disclaimer

This application is for **informational purposes only** and does not constitute legal advice. The data is sourced from official Austrian government APIs but may not be complete or current.

For legal matters, always consult a qualified attorney (Rechtsanwalt) in Austria.

---

## Acknowledgments

- [Austrian RIS System](https://www.ris.bka.gv.at/) - Official legal information system
- [RIS OGD API](https://data.bka.gv.at/) - Open Government Data API
- [Apache Lucene](https://lucene.apache.org/) - Search engine library
