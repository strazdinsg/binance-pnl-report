package no.strazdins;

import java.io.IOException;
import no.strazdins.file.ReportFileWriter;
import no.strazdins.process.Report;
import no.strazdins.process.ReportGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The main application runner - handles command-line arguments, calls the necessary logic.
 */
public class Runner {
  private static final String TRANSACTION_LOG_CSV_FILE = "transactions.csv";
  private static final Logger logger = LogManager.getLogger(Runner.class);

  /**
   * The main entrypoint of the application.
   *
   * @param args Command line arguments. Expected values: first argument is path to the
   *             input CSV-file, the second argument is path to the output file where the result
   *             will be written.
   */
  public static void main(String[] args) {
    try {
      String inputFilePath = getInputFilePath(args);
      String homeCurrency = getCurrency(args);
      String extraFilePath = getExtraFilePath(args);
      ReportGenerator reportGenerator = new ReportGenerator(inputFilePath, extraFilePath);
      Report report = reportGenerator.createReport();
      ReportFileWriter.writeTransactionLogToFile(report, TRANSACTION_LOG_CSV_FILE);
      logger.info("Reports successfully generated, saved in CSV files");
    } catch (IOException e) {
      logger.error("Report generation failed: {}", e.getMessage());
    }
  }

  private static String getInputFilePath(String[] args) throws IOException {
    if (args.length < 1) {
      throw new IOException(
          "The first command-line argument must contain path to the input file (CSV)");
    }

    return args[0];
  }

  private static String getCurrency(String[] args) throws IOException {
    if (args.length < 2) {
      throw new IOException(
          "The second command-line argument must contain the accounting currency (example: NOK)");
    }
    return args[1];
  }

  private static String getExtraFilePath(String[] args) throws IOException {
    if (args.length < 3) {
      throw new IOException(
          "The 3rd command-line argument must contain path to CSV file with extra information");
    }
    return args[2];
  }
}
