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

