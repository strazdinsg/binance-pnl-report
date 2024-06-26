package no.strazdins.file;


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Parses standard CSV files.
 */
public class CsvFileParser {
  private static final String COMMENT_CHARACTER = "#";
  private static final Logger log = LogManager.getLogger(CsvFileParser.class);
  private final BufferedReader reader;

  private String nextRow = null;

  private boolean isEndReached = false;

  /**
   * Create a new CSV file parser, try to open the CSV file.
   *
   * @param inputFilePath Path to the CSV file to process
   * @throws IOException When the file is not found or could not be read
   */
  public CsvFileParser(String inputFilePath) throws IOException {
    try {
      reader = new BufferedReader(new FileReader(inputFilePath));
    } catch (FileNotFoundException e) {
      throw new IOException("File not found: " + inputFilePath);
    }
  }

  /**
   * Check if there are unread rows left in the CSV file.
   *
   * @return True if there are more rows to read, false otherwise
   */
  public boolean hasMoreRows() {
    if (isEndReached) {
      return true;
    }

    // Check if there is another row available, buffer it
    if (nextRow == null) {
      fetchAndBufferNextRow();
    }

    return !isEndReached;
  }

  /**
   * Fetch the next row from the file, buffer it in the nextRow variable.
   * If end of file is reached, store null in nextRow.
   * Also update the "isEndReached" state.
   * Ignores commented-lines
   */
  private void fetchAndBufferNextRow() {
    try {
      do {
        nextRow = reader.readLine();
        if (nextRow != null && nextRow.startsWith(COMMENT_CHARACTER)) {
          log.error("Commented out row: {}", nextRow);
        }
      } while (nextRow != null && nextRow.startsWith(COMMENT_CHARACTER));
    } catch (IOException e) {
      nextRow = null;
    }
    if (nextRow == null) {
      isEndReached = true;
    }
  }

  /**
   * Read the next row from the CSV file, split it in separate cell-values.
   *
   * @return Values of each cell in the retrieved CSV-row.
   */
  public String[] readNextRow() {
    if (nextRow == null) {
      fetchAndBufferNextRow();
    }

    String[] result = null;
    if (nextRow != null) {
      result = removeDoubleQuotes(nextRow.split(","));
      nextRow = null; // Clear the cached row
    }
    return result;
  }

  /**
   * Go through all values, remove the double quotes.
   *
   * @param values The CSV-values to check
   * @return The same values, where double quotes are removed
   */
  private String[] removeDoubleQuotes(String[] values) {
    for (int i = 0; i < values.length; ++i) {
      values[i] = values[i].replace("\"", "");
    }
    return values;
  }
}
