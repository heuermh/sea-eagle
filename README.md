# sea-eagle

Command line tools for [AWS Athena](https://aws.amazon.com/athena/).

## Hacking sea-eagle

Install

 * JDK 17 or later, https://openjdk.java.net
 * Apache Maven 3.3.9 or later, https://maven.apache.org

To build
```bash
$ mvn package

$ export PATH=$PATH:`pwd`/target/appassembler/bin
```

## Using sea-eagle

### Usage

```bash
$ se --help
USAGE
  se [-hV] [--skip-header] [--skip-history] [-b=<outputLocation>] [-c=<catalog>] [-d=<database>] [-f=<resultsFormat>]
     [-i=<queryPath>] [--left-pad=<leftPad>] [-n=<pollingInterval>] [-o=<resultsPath>] [-q=<query>] [-w=<workgroup>]
     [-p=<executionParameters>]... [COMMAND]

OPTIONS
  -c, --catalog=<catalog>                          Catalog name, if any.
  -d, --database=<database>                        Database name, if any.
  -w, --workgroup=<workgroup>                      Workgroup, default primary.
  -b, --output-location=<outputLocation>           Output location, if workgroup is not provided.
  -n, --polling-interval=<pollingInterval>         Query status polling interval, default 250 ms.
      --skip-header                                Skip writing header to results.
      --skip-history                               Skip writing query to history file.
  -q, --query=<query>                              Inline SQL query, if any.
  -i, --query-path=<queryPath>                     SQL query input path, default stdin.
  -p, --execution-parameters=<executionParameters> SQL query execution parameters, if any.
  -o, --results-path=<resultsPath>                 Query results path, default stdout.
  -f, --format, --results-format=<resultsFormat>   Query results format { pretty, sparse, text, parquet }, default text.
      --left-pad=<leftPad>                         Left pad query results, default 2 for pretty and sparse formats.
      --verbose                                    Show additional logging messages.
  -h, --help                                       Show this help message and exit.
  -V, --version                                    Print version information and exit.

COMMANDS
  help                 Display help information about the specified command.
  generate-completion  Generate bash/zsh completion script for se.
```


### Environment variables

Note the `catalog`, `database`, `workgroup`, and `output-location` options can also be specified by
environment variables `SE_CATALOG`, `SE_DATABASE`, `SE_WORKGROUP`, and `SE_OUTPUT_LOCATION`, respectively
```bash
$ export SE_CATALOG=catalog

$ SE_WORKGROUP=workgroup \
    se \
      ... \
```


### SQL queries

SQL queries can be provided inline via the `-q`/`--query` option
```bash
$ se \
    ... \
    --query "SELECT * FROM table LIMIT 4"
```

By default the SQL query is read from `stdin`
```bash
$ echo "SELECT * FROM table LIMIT 4" | se \
    ... \
```

Or the SQL query can be read from a file via the `-i`/`--query-path` option
```bash
$ echo "SELECT * FROM table LIMIT 4" > query.sql

$ se \
    ... \
    --query-path query.sql
```


### Execution parameters

SQL queries may contain `?`-style execution parameters to be substituted server side
```bash
$ se \
    ... \
    --query "SELECT * FROM table WHERE foo = ? AND bar > ? LIMIT 4" \
    --execution-parameters baz \
    --execution-parameters 100000
```

Alternatively, variable substition can be done via e.g. `envsubst` on the client side
```bash
$ echo "SELECT * FROM table WHERE foo = '$FOO' LIMIT 4" > query.sql

$ export FOO=baz

$ envsubst < query.sql | se \
    ... \
```


### SQL query history file

SQL queries are written to a history file `~/.se_history`, unless `--skip-history` flag is present
```bash
$ se \
    ... \
    --query "SELECT * FROM table LIMIT 4"

$ se \
    ... \
    --skip-history \
    --query "SELECT * FROM table WHERE foo = 'top secret!!' LIMIT 4"

$ cat ~/.se_history
SELECT * FROM table LIMIT 4
```


### Output formats

#### Text and display formats

By default, results are written to `stdout` in tab-delimited text format.

This allows for easy integration with command line tools such as `cut`, `grep`, `awk`, `sed`,
`uniq`, etc. for post-processing.

```bash
$ se \
    ... \
    --query "SELECT * FROM table LIMIT 2"

foo	bar	baz
2088090022	185762	232298
2044078009	113652	85962


$ se \
    ... \
    --query "SELECT * FROM table LIMIT 4" \
    --skip-header | cut -f 4 | sort -n

26603
67310
116988
164738
```


Results may be formatted for display in the terminal, in sparse
```bash
$ se \
    ... \
    --query "SELECT * FROM table LIMIT 4" \
    --format sparse

      foo       bar       baz
   --------- --------- ---------
    1499494   2354616   5560703
     516330    758111   1623718
     113663    192870    137600
    1028323    960709    850306
```

and pretty formats
```bash
$ se \
    ... \
    --query "SELECT * FROM table LIMIT 4" \
    --format pretty

  +---------+---------+---------+
  |   foo   |   bar   |   baz   |
  +---------+---------+---------+
  | 1088718 | 1779849 | 5096779 |
  |   17560 |   40360 |   32204 |
  |      84 |    8273 |   47681 |
  |   52383 |  100406 |   86338 |
  +---------+---------+---------+
```

Results may be written to a file (and optionally compressed) via the `-o`/`--results-path` option
```bash
$ se \
    ... \
    --query "SELECT * FROM table LIMIT 4" \
    --results-path results.txt.zstd
```


#### Parquet format

Finally, results may be written out to a local Parquet file
```bash
$ se \
    ... \
    --query "SELECT * FROM table LIMIT 4" \
    --format parquet
    --results-path results.parquet
```

...which can easily be loaded into e.g. [duckdb](https://duckdb.org/) for further post-processing.
```sql
$ duckdb

D SELECT * FROM read_parquet("results.parquet");
┌─────────┬─────────┬─────────┐
│   foo   │   bar   │   baz   │
│  int64  │  int64  │  int64  │
├─────────┼─────────┼─────────┤
│ 1670466 │ 2455819 │ 5386130 │
│ 1427967 │ 1990921 │ 3779556 │
│   66473 │   97877 │   73903 │
│    7767 │    7766 │    5888 │
├─────────┴─────────┴─────────┤
│ 4 rows            3 columns │
└─────────────────────────────┘
```
