# sea-eagle

Command line tools for [AWS Athena]().

### Hacking sea-agle

Install

 * JDK 11 or later, https://openjdk.java.net
 * Apache Maven 3.3.9 or later, https://maven.apache.org

To build
```bash
$ mvn package

$ export PATH=$PATH:`pwd`/target/appassembler/bin

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
  -n, --polling-interval=<pollingInterval>         Query status polling interval, default 5,000 ms.
      --skip-header                                Skip writing header to results.
      --skip-history                               Skip writing query to history file.
  -q, --query=<query>                              Inline SQL query, if any.
  -i, --query-path=<queryPath>                     SQL query input path, default stdin.
  -p, --execution-parameters=<executionParameters> SQL query execution parameters, if any.
  -o, --results-path=<resultsPath>                 Query results path, default stdout.
  -f, --format, --results-format=<resultsFormat>   Query results format { pretty, sparse, text, parquet }, default text.
      --left-pad=<leftPad>                         Left pad query results, default 2 for pretty and sparse formats.
  -h, --help                                       Show this help message and exit.
  -V, --version                                    Print version information and exit.

COMMANDS
  help                 Display help information about the specified command.
  generate-completion  Generate bash/zsh completion script for se.
```
