/*
 * The authors of this file license it to you under the
 * Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You
 * may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.heuermh.seaeagle;

import static org.dishevelled.compress.Readers.reader;

import java.io.BufferedReader;
import java.io.IOException;

import java.nio.file.Path;

import java.util.List;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.AutoComplete.GenerateCompletion;

import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.ScopeType;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

import software.amazon.awssdk.services.athena.AthenaClient;

import software.amazon.awssdk.services.athena.model.QueryExecutionContext;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.AthenaException;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Row;

import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;

/**
 * Sea eagle.
 */
@Command(
  name = "se",
  scope = ScopeType.INHERIT,
  subcommands = {
      HelpCommand.class,
      GenerateCompletion.class
  },
  mixinStandardHelpOptions = true,
  sortOptions = false,
  usageHelpAutoWidth = true,
  resourceBundle = "com.github.heuermh.seaeagle.Messages",
  versionProvider = com.github.heuermh.seaeagle.About.class
)
public final class SeaEagle implements Callable<Integer> {

    // todo: consider pulling these out of a config file
    @picocli.CommandLine.Option(names = { "-c", "--catalog" })
    private String catalog;

    @picocli.CommandLine.Option(names = { "-d", "--database" })
    private String database;

    @picocli.CommandLine.Option(names = { "-w", "--workgroup" })
    private String workgroup = DEFAULT_WORKGROUP;

    @picocli.CommandLine.Option(names = { "-b", "--output-location" })
    private String outputLocation;

    @picocli.CommandLine.Option(names = { "-n", "--polling-interval" })
    private long pollingInterval = DEFAULT_POLLING_INTERVAL;

    @picocli.CommandLine.Option(names = { "--skip-header" })
    private boolean skipHeader;

    @picocli.CommandLine.Option(names = { "--skip-history" })
    private boolean skipHistory;

    @picocli.CommandLine.Option(names = { "-q", "--query" })
    private String query;

    @picocli.CommandLine.Option(names = { "-i", "--query-path" })
    private Path queryPath;

    @picocli.CommandLine.Option(names = { "-p", "--parameters", "--execution-parameters" })
    private List<String> executionParameters;

    @picocli.CommandLine.Option(names = { "-o", "--results-path" })
    private Path resultsPath;

    @picocli.CommandLine.Option(names = { "-f", "--format", "--results-format" })
    private String resultsFormat = "text";

    @picocli.CommandLine.Option(names = { "--left-pad" })
    private int leftPad = 2;

    // todo: verbose to change log level at runtime


    private final HistoryFile historyFile = new HistoryFile();

    static final String DEFAULT_WORKGROUP = "primary";
    static final long DEFAULT_POLLING_INTERVAL = 250L;

    static final Logger logger = LoggerFactory.getLogger(SeaEagle.class);

    @Override
    public Integer call() throws Exception {

        // prepare query from inline or query path
        if (query == null) {
            logger.info("Reading SQL query from path {}", queryPath == null ? "<stdin>" : queryPath);
            query = readQueryPath();
        }

        // write query to history file
        if (!skipHistory) {
            historyFile.append(query);
        }

        // create athena client
        logger.info("Creating Athena client with profile credentials provider");
        AthenaClient athenaClient = AthenaClient.builder()
            .credentialsProvider(ProfileCredentialsProvider.create())
            .build();

        //
        // submit query and poll for results
        logger.info("Submitting SQL query to Athena");

        String queryExecutionId = null;
        try {
            queryExecutionId = submitAthenaQuery(athenaClient);
        }
        catch (AthenaException e) {
            logger.error("Could not submit SQL query to Athena, caught exception", e);
            return 1;
        }

        logger.info("Received query execution ID {}, polling for successful query execution state", queryExecutionId);
        try {
            pollUntilComplete(athenaClient, queryExecutionId);
        }
        catch (InterruptedException e) {
            logger.error("Could not poll for query execution ID {} status, interrupted", queryExecutionId, e);
            return 1;
        }
        catch (CanceledException e) {
            logger.error("Query execution for ID {} canceled", queryExecutionId, e);
            return 1;
        }
        catch (FailedException e) {
            logger.error("Query execution for ID {} failed", queryExecutionId, e);
            return 1;
        }

        logger.info("Query execution for ID {} complete, processing results", queryExecutionId);
        try {
            processResults(athenaClient, queryExecutionId);
        }
        catch (AthenaException | IOException e) {
            logger.error("Could not process results for query execution ID {}, caught exception", queryExecutionId, e);
            return 1;
        }

        // todo: is this necessary?  move to try-with-resources block?
        athenaClient.close();

        return 0;
    }


    String readQueryPath() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = reader(queryPath)) {
            while (reader.ready()) {
                String line = reader.readLine();
                sb.append(line);
                sb.append(" ");
            }
        }
        catch (IOException e) {
            logger.error("Unable to read SQL from {}", queryPath == null ? "<stdin>" : queryPath);
            throw e;
        }
        return sb.toString().trim().replace("\\s+", " ");
    }

    String submitAthenaQuery(final AthenaClient athenaClient) throws AthenaException {
        // configure query execution context
        QueryExecutionContext.Builder queryExecutionContextBuilder = QueryExecutionContext.builder();
        if (catalog != null) {
            queryExecutionContextBuilder.catalog(catalog);
        }
        if (database != null) {
            queryExecutionContextBuilder.database(database);
        }
        QueryExecutionContext queryExecutionContext = queryExecutionContextBuilder.build();

        // configure result configuration
        ResultConfiguration.Builder resultConfigurationBuilder = ResultConfiguration.builder();
        if (outputLocation != null) {
            resultConfigurationBuilder.outputLocation(outputLocation);
        }
        ResultConfiguration resultConfiguration = resultConfigurationBuilder.build();

        // configure start query execution request
        StartQueryExecutionRequest.Builder startQueryExecutionRequestBuilder = StartQueryExecutionRequest.builder();
        if (workgroup != null) {
            startQueryExecutionRequestBuilder.workGroup(workgroup);
        }
        if (executionParameters != null) {
            startQueryExecutionRequestBuilder.executionParameters(executionParameters);
        }
        startQueryExecutionRequestBuilder.queryString(query);
        startQueryExecutionRequestBuilder.queryExecutionContext(queryExecutionContext);
        startQueryExecutionRequestBuilder.resultConfiguration(resultConfiguration);
        StartQueryExecutionRequest request = startQueryExecutionRequestBuilder.build();

        // start query execution
        StartQueryExecutionResponse response = athenaClient.startQueryExecution(request);
        return response.queryExecutionId();
    }

    void pollUntilComplete(final AthenaClient athenaClient, final String queryExecutionId) throws InterruptedException, CanceledException, FailedException {
        GetQueryExecutionRequest request = GetQueryExecutionRequest.builder()
            .queryExecutionId(queryExecutionId)
            .build();

        boolean running = true;
        while (running) {
            GetQueryExecutionResponse response = athenaClient.getQueryExecution(request);
            QueryExecutionState current = response.queryExecution().status().state();
            switch (current) {
                case CANCELLED:
                    throw new CanceledException();
                case FAILED:
                    // see also AthenaError
                    throw new FailedException(response.queryExecution().status().stateChangeReason());
                case SUCCEEDED:
                    // see also QueryExecutionStatistics
                    running = false;
                    break;
                case QUEUED:
                case RUNNING:
                case UNKNOWN_TO_SDK_VERSION:
                    logger.info("Query execution for ID {} still running", queryExecutionId);
                    Thread.sleep(pollingInterval);
            }
        }
    }

    void processResults(final AthenaClient athenaClient, final String queryExecutionId) throws AthenaException, IOException {
        try (ResultsProcessor processor = createProcessor()) {
            GetQueryResultsRequest request = GetQueryResultsRequest.builder()
                .queryExecutionId(queryExecutionId)
                .build();

            GetQueryResultsIterable results = athenaClient.getQueryResultsPaginator(request);
            for (GetQueryResultsResponse result : results) {
                List<Row> rows = result.resultSet().rows();
                List<ColumnInfo> columns = result.resultSet().resultSetMetadata().columnInfo();
                processor.columns(columns);
                processor.rows(columns, rows);
            }
            processor.complete();
        }
    }

    ResultsProcessor createProcessor() {
        switch (resultsFormat) {
            case "parquet":
                // todo: move this check earlier?
                if (resultsPath == null) {
                    throw new IllegalArgumentException("parquet format requires --results-path to be set");
                }
                return new ParquetFormat(resultsPath);
            case "pretty":
                if (skipHeader) {
                    return new PrettyTableFormat(resultsPath, leftPad);
                }
                else {
                    return new PrettyTableWithHeaderFormat(resultsPath, leftPad);
                }
            case "sparse":
                if (skipHeader) {
                    return new SparseTableFormat(resultsPath, leftPad);
                }
                else {
                    return new SparseTableWithHeaderFormat(resultsPath, leftPad);
                }
            case "tui":
                return new TuiFormat();
            case "text":
            case "tsv":
            case "tab-delimited":
            default:
                if (skipHeader) {
                    return new TabDelimitedFormat(resultsPath);
                }
                else {
                    return new TabDelimitedWithHeaderFormat(resultsPath);
                }
        }
    }


    /**
     * Main.
     *
     * @param args command line args
     */
    public static void main(final String[] args) {
        System.exit(new CommandLine(new SeaEagle()).execute(args));
    }
}
