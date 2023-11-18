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

import static org.dishevelled.compress.Writers.writer;

import java.io.IOException;
import java.io.PrintWriter;

import java.nio.file.Path;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ImmutableMap;

import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.Row;

/**
 * Parquet format.
 */
class ParquetFormat extends ResultsProcessor {
    private final String url;
    private final Path resultsPath;
    private final String tableName;
    private final String parquetCodec;
    private String createSql;
    private String insertSql;
    private String copySql;
    private Connection connection;
    private PreparedStatement insertStatement;
    private boolean seenHeader = false;
    protected boolean seenHeaderRow = false;

    static final ImmutableMap<String, String> TYPE_NAMES = new ImmutableMap.Builder<String, String>()
        .put("boolean", "BIT") // or boolean?
        .put("tinyint", "TINYINT")
        .put("smallint", "SMALLINT")
        .put("integer", "INTEGER")
        .put("bigint", "BIGINT")
        .put("double", "DOUBLE")
        .put("float", "REAL") // or float?
        .put("decimal", "DECIMAL") // decimal(precision, scale)
        .put("char", "CHAR")
        .put("varchar", "VARCHAR")
        .put("string", "VARCHAR")
        .put("binary", "VARBINARY")
        .put("date", "DATE")
        .put("timestamp", "TIMESTAMP")
        .put("array", "VARCHAR") // array<type>
        .put("map", "VARCHAR") // map<primitive_type, type>
        .put("struct", "VARCHAR") // struct<col_name : data_type, ...>
        .buildOrThrow();

    static final ImmutableMap<String, Integer> TYPES = new ImmutableMap.Builder<String, Integer>()
        .put("boolean", Types.BIT)
        .put("tinyint", Types.TINYINT)
        .put("smallint", Types.SMALLINT)
        .put("integer", Types.INTEGER)
        .put("bigint", Types.BIGINT)
        .put("double", Types.DOUBLE)
        .put("float", Types.REAL) // or float?
        .put("decimal", Types.DECIMAL) // decimal(precision, scale)
        .put("char", Types.CHAR)
        .put("varchar", Types.VARCHAR)
        .put("string", Types.VARCHAR)
        .put("binary", Types.VARBINARY)
        .put("date", Types.DATE)
        .put("timestamp", Types.TIMESTAMP)
        .put("array", Types.VARCHAR) // array<type>
        .put("map", Types.VARCHAR) // map<primitive_type, type>
        .put("struct", Types.VARCHAR) // struct<col_name : data_type, ...>
        .buildOrThrow();


    ParquetFormat(final String url, final String tableName, final String parquetCodec, final Path resultsPath) {
        // todo: defaults, resultsPath must not be null
        this.url = url;
        this.tableName = tableName;
        this.parquetCodec = parquetCodec;
        this.resultsPath = resultsPath;
    }


    void createConnection() throws IOException {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
            connection = DriverManager.getConnection(url);
        }
        catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    void columns(final List<ColumnInfo> columns) throws IOException {
        if (!seenHeader) {
            createConnection();

            StringBuilder create = new StringBuilder();
            create.append("CREATE TABLE ");
            create.append(tableName);
            create.append(" (");

            StringBuilder insert = new StringBuilder();
            insert.append("INSERT INTO ");
            insert.append(tableName);
            insert.append(" (");

            StringBuilder copy = new StringBuilder();
            copy.append("COPY ");
            copy.append(tableName);
            copy.append(" TO '");
            copy.append(resultsPath.toString());
            copy.append("' (FORMAT 'PARQUET', CODEC '");
            copy.append(parquetCodec);
            copy.append("')");

            for (Iterator<ColumnInfo> it = columns.iterator(); it.hasNext(); ) {
                ColumnInfo columnInfo = it.next();
                String columnName = columnInfo.name();
                String columnType = columnInfo.type();

                create.append(columnName);
                create.append(" ");
                create.append(TYPE_NAMES.get(columnType));

                insert.append(columnName);

                if (it.hasNext()) {
                    create.append(", ");
                    insert.append(", ");
                }
            }
            create.append(")");

            insert.append(") VALUES (");
            for (Iterator<ColumnInfo> it = columns.iterator(); it.hasNext(); ) {
                it.next();                
                insert.append("?");
                if (it.hasNext()) {
                    insert.append(", ");
                }
            }
            insert.append(")");

            createSql = create.toString();
            insertSql = insert.toString();
            copySql = copy.toString();

            seenHeader = true;
        }

        try (Statement createStatement = connection.createStatement()) {
            createStatement.execute(createSql);
        }
        catch (SQLException e) {
            throw new IOException(e);
        }
        try {
            insertStatement = connection.prepareStatement(insertSql);
        }
        catch (SQLException e) {
            throw new IOException(e);
        }
    }

    // sigh... 
    protected final boolean isHeaderRow(final List<ColumnInfo> columns, final Row row) {
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
                try {
                    insertStatement.clearParameters();
                    for (int i = 0; i < row.data().size(); i++) {
                        Datum datum = row.data().get(i);
                        ColumnInfo columnInfo = columns.get(i);
                        String columnType = columnInfo.type();
                        insertStatement.setObject(i + 1, datum.varCharValue(), TYPES.get(columnType));
                    }
                    insertStatement.executeUpdate();
                }
                catch (SQLException e) {
                    throw new IOException (e);
                }
            }
        }
    }

    @Override
    public void complete() throws IOException {
        try (Statement copy = connection.createStatement()) {
            copy.execute(copySql);
        }
        catch (SQLException e) {
            throw new IOException (e);
        }
    }

    @Override
    public void close() {
        if (insertStatement != null) {
            try {
                insertStatement.close();
            }
            catch (Exception e) {
                // ignore
            }
        }
        if (connection != null) {
            try {
                connection.close();
            }
            catch (Exception e) {
                // ignore
            }
        }
    }
}
