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
import static org.dishevelled.compress.Writers.writer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.Path;

import java.util.Iterator;
import java.util.List;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

import software.amazon.awssdk.regions.Region;

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
import software.amazon.awssdk.services.athena.model.Datum;

import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;

/**
 * Query.
 */
@Command(name = "query")
public final class Query implements Callable<Integer> {

    // todo: consider pulling these out of a config file
    @Option(names = { "-c", "--catalog" })
    private String catalog = DEFAULT_CATALOG;

    @Option(names = { "-d", "--database" })
    private String database = DEFAULT_DATABASE;

    @Option(names = { "-w", "--workgroup" })
    private String workgroup = DEFAULT_WORKGROUP;
    
    //@Option(names = { "-r", "--region" })
    // No TypeConverter registered for software.amazon.awssdk.regions.Region of
    // field software.amazon.awssdk.regions.Region com.github.heuermh.seaeagle.Query.region
    private Region region = DEFAULT_REGION;

    // not necessary if workgroup provides, use URI/URL?
    @Option(names = { "-b", "--output-bucket" })
    private String outputBucket;

    @Option(names = { "-n", "--polling-interval" })
    private long pollingInterval = DEFAULT_POLLING_INTERVAL;

    @Option(names = { "--skip-history" })
    private boolean skipHistory;

    @Option(names = { "-q", "--sql" })
    private String sql; // queryString

    @Option(names = { "-i", "--sql-path" })
    private Path sqlPath;

    @Option(names = { "-p", "--parameters", "--execution-parameters" })
    private List<String> executionParameters; // how are these provided?

    @Option(names = { "-o", "--results-path" })
    private Path resultsPath;

    @Option(names = { "-f", "--format", "--results-format" })
    private String resultsFormat; // to enum

    // todo: verbose to change log level at runtime


    private final HistoryFile historyFile = new HistoryFile();

    static final String DEFAULT_CATALOG = "";
    static final String DEFAULT_DATABASE = "";
    static final Region DEFAULT_REGION = Region.US_WEST_2;
    static final String DEFAULT_WORKGROUP = "primary";
    static final long DEFAULT_POLLING_INTERVAL = 5000L;

    static final Logger logger = LoggerFactory.getLogger(Query.class);

    @Override
    public Integer call() throws Exception {

        // prepare sql query from inline or sql path
        if (sql == null) {
            logger.info("Reading SQL query from path {}", sqlPath == null ? "<stdin>" : sqlPath);
            sql = readSqlPath();
        }

        // write query to history file
        if (!skipHistory) {
            historyFile.append(sql);
        }

        // create athena client
        logger.info("Creating Athena client for region {}", region);
        AthenaClient athenaClient = AthenaClient.builder()
            .region(region)
            .credentialsProvider(ProfileCredentialsProvider.create())
            .build();

        //
        // submit query and poll for results

        //logger.info("Submitting SQL query to Athena with database {}, output bucket {}", database, outputBucket);
        logger.info("Submitting SQL query to Athena with database {}", database);

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
        catch (AthenaException e) {
            logger.error("Could not process results for query execution ID {}, caught exception", e);
            return 1;
        }
        catch (IOException e) {
            logger.error("Could not process results for query execution ID {}, caught exception", e);
            return 1;
        }

        // todo: is this necessary?  move to try-with-resources block?
        athenaClient.close();

        return 0;
    }


    String readSqlPath() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = reader(sqlPath)) {            
            while (reader.ready()) {
                String line = reader.readLine();
                sb.append(line);
                sb.append(" ");
            }
        }
        catch (IOException e) {
            logger.error("Unable to read SQL from {}", sqlPath == null ? "<stdin>" : sqlPath);
            throw e;
        }
        return sb.toString().trim().replace("\\s+", " ");
    }

    String submitAthenaQuery(final AthenaClient athenaClient) throws AthenaException {
        QueryExecutionContext queryExecutionContext = QueryExecutionContext.builder()
            //.catalog(catalog)
            .database(database)
            .build();

        ResultConfiguration resultConfiguration = ResultConfiguration.builder()
            //.outputLocation(outputBucket) not necessary if use workgroup
            .build();

        StartQueryExecutionRequest request = StartQueryExecutionRequest.builder()
            .queryString(sql)
            //.executionParameters(...)
            .queryExecutionContext(queryExecutionContext)
            .resultConfiguration(resultConfiguration)
            .workGroup(workgroup)
            .build();

        StartQueryExecutionResponse response = athenaClient.startQueryExecution(request);
        return response.queryExecutionId();
    }

    static class CanceledException extends Exception {
        CanceledException() {
            super((String) null);
        }
    }

    static class FailedException extends Exception {
        FailedException(final String message) {
            super(message);
        }
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
        try (Processor processor = createProcessor()) {
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
        }
    }

    abstract class Processor implements AutoCloseable {
        void columns(final List<ColumnInfo> columns) throws IOException {
            // empty
        }

        void rows(final List<ColumnInfo> columns, final List<Row> rows) throws IOException {
            // empty
        }

        @Override
        public void close() {
            // empty
        }
    }

    Processor createProcessor() {
        return new TabDelimitedWithHeaderFormat();
    }

    class TabDelimitedFormat extends Processor {
        private PrintWriter writer;
        private boolean seenHeaderRow = false;

        protected final PrintWriter getWriter() throws IOException {
            if (writer == null) {
                writer = writer(resultsPath);
            }
            return writer;
        }

        // sigh... 
        boolean isHeaderRow(final List<ColumnInfo> columns, final Row row) {
            if (columns.isEmpty()) {
                return false;
            }
            if (row.data().isEmpty()) {
                return false;
            }
            String firstColumnName = columns.get(0).name();
            String firstRowValue = row.data().get(0).varCharValue();

            if (firstColumnName.equals(firstRowValue)) {
                seenHeaderRow = true;
                return true;
            }
            return false;
        }

        @Override
        void rows(final List<ColumnInfo> columns, final List<Row> rows) throws IOException {
            for (Row row : rows) {
                if (seenHeaderRow || !isHeaderRow(columns, row)) {
                    StringBuilder sb = new StringBuilder();
                    for (Iterator<Datum> it = row.data().iterator(); it.hasNext(); ) {
                        sb.append(it.next().varCharValue());
                        if (it.hasNext()) {
                            sb.append("\t");
                        }
                    }
                    getWriter().println(sb.toString());
                }
            }
        }

        @Override
        public void close() {
            if (writer != null) {
                writer.close();
            }
        }
    }

    class TabDelimitedWithHeaderFormat extends TabDelimitedFormat {
        private boolean wroteHeader = false;

        @Override
        void columns(final List<ColumnInfo> columns) throws IOException {
            if (!wroteHeader) {
                StringBuilder sb = new StringBuilder();
                for (Iterator<ColumnInfo> it = columns.iterator(); it.hasNext(); ) {

                    // fqn? catalogName(), schemaName(), tableName(), name()                
                    sb.append(it.next().name());
                    if (it.hasNext()) {
                        sb.append("\t");
                    }
                }
                getWriter().println(sb.toString());
                wroteHeader = true;
            }
        }
    }


    /**
     * Main.
     *
     * @param args command line args
     */
    public static void main(final String[] args) {
        System.exit(new CommandLine(new Query()).execute(args));
    }
}
