# Austrian Law Search (Pocket Lawyer)

A Java 21 application that crawls Austrian law databases (RIS - Rechtsinformationssystem), creates an indexed database, and provides intelligent legal search with context-aware responses in German and English.

## Features

- **RIS Database Crawler**: Crawls the official Austrian RIS OGD API for laws, regulations, and court decisions
- **Full-Text Search**: Powered by Apache Lucene with German language analysis
- **Natural Language Queries**: Search in German or English
- **Legal Reference Detection**: Automatically detects and links citations like "§ 14 VersG"
- **Context-Aware Responses**: Generates comprehensive legal summaries
- **Police/Authority Text**: Creates official text with legal references for authorities
- **Journaling System**: Tracks all operations for session resumability

## Data Sources

The application crawls the following from the Austrian RIS system:
- **Bundesrecht** (Federal Laws)
- **Landesrecht** (State Laws)
- **VfGH** (Constitutional Court Decisions)
- **VwGH** (Administrative Court Decisions)
- **OGH/Justiz** (Supreme Court Decisions)

## Requirements

- Java 21 or higher
- Maven 3.8+
- Internet connection (for crawling RIS API)

## Installation

```bash
# Clone the repository
git clone https://github.com/qwitch13/law-search.git
cd law-search

# Build the project
mvn package -DskipTests

# Run the application
java -jar target/law-search-1.0.0-SNAPSHOT.jar --help
```

## Usage

### Interactive Mode
```bash
java -jar target/law-search-1.0.0-SNAPSHOT.jar --interactive
```

### Crawl the RIS Database
```bash
java -jar target/law-search-1.0.0-SNAPSHOT.jar --crawl
```

### Search Queries
```bash
# Search by keyword
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "Versammlungsfreiheit"

# Search by legal reference
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "§ 14 VersG"

# Search in English
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "demonstration rights"

# Generate police/authority text
java -jar target/law-search-1.0.0-SNAPSHOT.jar -q "spontaneous assembly" --police-text
```

### Interactive Commands
- `/help` - Show help
- `/crawl` - Crawl RIS database
- `/stats` - Show index statistics
- `/police` - Toggle police text output
- `/quit` - Exit

## Search Syntax

| Operator | Example | Description |
|----------|---------|-------------|
| `+term` | `+spontan +versammlung` | Required term |
| `-term` | `polizei -gewalt` | Exclude term |
| `"phrase"` | `"Versammlungsgesetz"` | Exact phrase |
| `§ 14 VersG` | Direct law reference | Search by citation |

## Example: Demonstration Rights

Query: "demo rights spontaneous demonstration"

The system will:
1. Find relevant laws (VersG, SPG, etc.)
2. Find related court decisions
3. Generate a summary explaining your rights
4. Create official text for police with:
   - Legal basis for spontaneous assemblies
   - Requirements (political purpose, 3+ people, visible signs)
   - Police obligations (must give reasons for dissolution)
   - Your rights and limitations

## Project Structure

```
law-search/
├── src/main/java/at/law/search/
│   ├── LawSearchApplication.java   # Main entry point
│   ├── cli/                        # Command-line interface
│   ├── crawler/                    # RIS API crawler
│   ├── index/                      # Lucene indexer
│   ├── journal/                    # Session journaling
│   ├── model/                      # Domain models
│   ├── response/                   # Response generation
│   └── search/                     # Search engine
├── pom.xml                         # Maven configuration
├── CLAUDE.md                       # Development journal
└── README.md                       # This file
```

## Technology Stack

- **Java 21** - Modern Java with records, sealed classes, pattern matching
- **Apache Lucene 9.9** - Full-text search with German analysis
- **Jackson** - JSON/XML processing
- **Picocli** - Command-line interface
- **SLF4J + Logback** - Logging
- **Jsoup** - HTML parsing

## API Reference

### RIS OGD API v2.6
- Base URL: `https://data.bka.gv.at/ris/api/v2.6/`
- Documentation: [OGD-RIS API Handbuch](https://data.bka.gv.at/ris/ogd/v2.6/Documents/Dokumentation_OGD-RIS_API.pdf)

## License

MIT License

## Disclaimer

This application is for informational purposes only and does not constitute legal advice. For legal matters, always consult a qualified attorney.
