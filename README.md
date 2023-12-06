# sea-eagle

Command line tools for [AWS Athena](https://aws.amazon.com/athena/).

### Hacking sea-eagle

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
  -n, --polling-interval=<pollingInterval>         Query status polling interval, default 250 ms.
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

For example
```bash
$ se \
  --database database \
  --workgroup workgroup \
  --query "select foo, bar, baz from example" \
  --format sparse

      foo       bar       baz
   --------- --------- ---------
    1499494   2354616   5560703
     516330    758111   1623718
     113663    192870    137600
    1028323    960709    850306
      93400    106270    222614
     122802    205962    126434
     353471    559598   1481814
        189      5942     42922
      75050     82266     23910
     250471    360182   1020279
      40296     46812    118579
     118357    150441     95300
     201463    440648    366368
     112916    133982     57178
     100463    133405     78405
      85073    142120    464772
      36545     39175    108751
        141        99       105
     206530    273974    140601
     117219    128936    306981
         68      7640      8562
     220511   1095658    956314
      63979    161020    137316
     272094    366378   1056719
```
