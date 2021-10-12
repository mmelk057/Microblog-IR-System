# Microblog-IR-System
An Information Retrieval (IR) system based for a collection of Twitter messages. Based on the TREC 2011 Microblog retrieval task

# Run Application

Gradle is the build system, using a Groovy DSL to compile, run tests (not applicable), manage dependencies, etc. 

```shell
./gradlew run -PjvmArgs="-DresultsDir=<INSERT_DIRECTORY_PATH>"
```

`resultsDir` : JVM Argument that defines the directory to output the `Results.txt` file containing retrieval results

If you have your JAR already built, then execute the following command:
```shell
java -jar <JAR_NAME> -DresultsDir=<INSERT_DIRECTORY_PATH>
```