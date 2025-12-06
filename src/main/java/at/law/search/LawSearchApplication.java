package at.law.search;

import at.law.search.cli.CommandLineInterface;
import picocli.CommandLine;

/**
 * Main entry point for the Austrian Law Search application.
 *
 * This application provides:
 * - Crawling of Austrian RIS (Rechtsinformationssystem) databases
 * - Full-text search of laws, regulations, and court decisions
 * - Natural language queries in German and English
 * - Context-aware legal responses
 * - Official text generation for authorities (police, officials)
 *
 * Usage:
 *   java -jar law-search.jar --help              # Show help
 *   java -jar law-search.jar --interactive       # Interactive mode
 *   java -jar law-search.jar --crawl             # Crawl RIS database
 *   java -jar law-search.jar -q "§ 14 VersG"     # Search query
 *   java -jar law-search.jar -q "demo rights" --police-text
 *
 * @author Law Search Team
 * @version 1.0.0
 */
public class LawSearchApplication {

    public static final String VERSION = "1.0.0";
    public static final String NAME = "Austrian Law Search";

    public static void main(String[] args) {
        // Use Picocli to parse arguments and run CLI
        int exitCode = new CommandLine(new CommandLineInterface())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args);

        System.exit(exitCode);
    }
}
