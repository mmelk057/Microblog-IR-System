# Microblog-IR-System
An Information Retrieval (IR) system based for a collection of Twitter messages. Based on the TREC 2011 Microblog retrieval task

# Run Application

Gradle is the build system, using a Groovy DSL to compile, run tests (not applicable), manage dependencies, etc. 

Run this from the root of the directory:
```shell
./gradlew clean build
```

`resultsDir` : JVM Argument that defines the directory to output the `Results.txt` file containing retrieval results

**JDK >= 15.0.2 is required**
```shell
cd .\build\libs\
java -jar -DresultsDir="<INSERT_DIRECTORY_PATH>" .\<JAR_NAME>
```

Baseline Results

```
runid                   all     myRun
num_q                   all     49
num_ret                 all     32220
num_rel                 all     2640
num_rel_ret             all     1820
map                     all     0.2183
gm_map                  all     0.1097
Rprec                   all     0.2626
bpref                   all     0.2461
recip_rank              all     0.5265
iprec_at_recall_0.00    all     0.6247
iprec_at_recall_0.10    all     0.4592
iprec_at_recall_0.20    all     0.3738
iprec_at_recall_0.30    all     0.3316
iprec_at_recall_0.40    all     0.2915
iprec_at_recall_0.50    all     0.2297
iprec_at_recall_0.60    all     0.1745
iprec_at_recall_0.70    all     0.1209
iprec_at_recall_0.80    all     0.0715
iprec_at_recall_0.90    all     0.0378
iprec_at_recall_1.00    all     0.0114
P_5                     all     0.3918
P_10                    all     0.3306
P_15                    all     0.2844
P_20                    all     0.2745
P_30                    all     0.2585
P_100                   all     0.1639
P_200                   all     0.1182
P_500                   all     0.0677
P_1000                  all     0.0371
```

Word2Vec Optimization

```
runid                   all     myRun
num_q                   all     49
num_ret                 all     32220
num_rel                 all     2640
num_rel_ret             all     1820
map                     all     0.2441
gm_map                  all     0.1292
Rprec                   all     0.2803
bpref                   all     0.2726
recip_rank              all     0.5459
iprec_at_recall_0.00    all     0.6439
iprec_at_recall_0.10    all     0.4784
iprec_at_recall_0.20    all     0.4094
iprec_at_recall_0.30    all     0.3612
iprec_at_recall_0.40    all     0.3213
iprec_at_recall_0.50    all     0.2713
iprec_at_recall_0.60    all     0.1988
iprec_at_recall_0.70    all     0.1349
iprec_at_recall_0.80    all     0.0873
iprec_at_recall_0.90    all     0.0440
iprec_at_recall_1.00    all     0.0117
P_5                     all     0.4286
P_10                    all     0.3612
P_15                    all     0.3279
P_20                    all     0.3112
P_30                    all     0.2891
P_100                   all     0.1933
P_200                   all     0.1391
P_500                   all     0.0704
P_1000                  all     0.0371
```
