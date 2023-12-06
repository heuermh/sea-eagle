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

```bash
$ se \
  --database database \
  --workgroup workgroup \
  --query "select foo, bar, baz from example" \
  --format pretty

  +---------+---------+---------+
  |   foo   |   bar   |   baz   |
  +---------+---------+---------+
  | 1088718 | 1779849 | 5096779 |
  |   17560 |   40360 |   32204 |
  |      84 |    8273 |   47681 |
  |   52383 |  100406 |   86338 |
  |   60978 |  116895 |   92954 |
  |  582231 |  624177 |  873897 |
  |   60105 |   96224 |   70939 |
  |   26706 |   29724 |   61980 |
  |   71041 |   85657 |   44456 |
  |    1092 |    1090 |    2161 |
  |   45114 |   50299 |  100645 |
  |      36 |   11675 |   90045 |
  |  914478 |  943877 | 1812353 |
  |  331784 |  428479 | 1207972 |
  |    2804 |    2806 |    5868 |
  |  112934 |  158499 |  453591 |
  |   78382 |  117840 |   81177 |
  |  601579 |  726169 | 1377316 |
  |   66397 |  586665 |  642583 |
  |  162038 |  479360 |  414506 |
  |     754 |     700 |    1374 |
  |    8378 |   29904 |   19937 |
  |   78669 |  164224 |  107699 |
  |  495637 |  668429 | 1095699 |
  +---------+---------+---------+
```

```bash
$ se \
  --database database \
  --workgroup workgroup \
  --query "select foo, bar, baz from example" \
  --results-format parquet \
  --results-path foo.parquet

$ duckdb
D select * from read_parquet("foo.parquet");
┌─────────┬─────────┬─────────┐
│   foo   │   bar   │   baz   │
│  int64  │  int64  │  int64  │
├─────────┼─────────┼─────────┤
│ 1670466 │ 2455819 │ 5386130 │
│ 1427967 │ 1990921 │ 3779556 │
│   66473 │   97877 │   73903 │
│    7767 │    7766 │    5888 │
│  592539 │  680396 │ 1316416 │
│   55862 │  127827 │   86968 │
│  312925 │  379713 │  668032 │
│   53477 │   99411 │  236360 │
│  214155 │  240226 │  472594 │
│     433 │    2264 │   16631 │
│   66180 │  122619 │   90052 │
│   49876 │   58304 │  126477 │
│   28205 │   31872 │   97107 │
│   68561 │  129016 │  471649 │
│  163444 │  200285 │  450191 │
│  557512 │  700447 │ 1267954 │
│  884122 │  996617 │ 1747037 │
│  359649 │  367325 │  387756 │
│  138393 │  238236 │  479819 │
│   42038 │   57246 │  173395 │
│   85615 │  160421 │  116326 │
│   59027 │  114297 │   91653 │
│  509225 │  938199 │ 1455539 │
│  652027 │ 1016476 │ 1045162 │
├─────────┴─────────┴─────────┤
│ 24 rows           3 columns │
└─────────────────────────────┘
```
