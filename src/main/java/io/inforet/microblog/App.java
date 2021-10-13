package io.inforet.microblog;

import io.inforet.microblog.entities.InfoDocument;
import io.inforet.microblog.entities.Query;
import io.inforet.microblog.tokenization.MicroblogTokenizer;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class App {

    public static final String OUTPUT_FILE_ARG = "resultsDir";
    public static final String RESULTS_FILE_NAME = "Results.txt";
    public static final String TREC_DATASET = "trec-dataset.txt";
    public static final String TREC_QUERIES = "trec-queries.xml";
    public static final String STOP_WORDS = "stop-words.txt";

    /**
     * Parses a generic list of entries from a given fileName
     * @param fileName Relative path
     * @param fileCallback Callback to invoke, providing the fileName, with the expectation of a generic list of entries
     * @param <T> Entry type
     * @return Generic list of entries
     */
    public static <T> Collection<T> loadFileEntries(String fileName, Function<String, Collection<T>> fileCallback) {
        URL datasetURL = Thread.currentThread().getContextClassLoader().getResource(fileName);
        Collection<T> parsedDocuments = null;
        if (datasetURL != null) {
            parsedDocuments = fileCallback.apply(datasetURL.getPath());
        }
        return parsedDocuments != null ? parsedDocuments : new LinkedList<>();
    }


    public static void main(String[] args) {
        // (1) Parse the directory argument where the TREC results file will reside
        String outputPath = System.getProperty(OUTPUT_FILE_ARG);
        if (outputPath == null || outputPath.isEmpty()) {
            throw new IllegalArgumentException(String.format("Mandatory argument: '-D%s' not provided...", OUTPUT_FILE_ARG));
        }
        try {
            Path resultsDir = Paths.get(outputPath);
            if (!Files.isDirectory(resultsDir) || !Files.exists(resultsDir)) {
                throw new IllegalArgumentException(String.format("The following argument: '-D%s' must be a directory that exists. Provided: '%s'", OUTPUT_FILE_ARG, outputPath));
            }
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException(String.format("The following argument: '-D%s' must be a well-formed directory that exists. Malformed input provided: '%s'", OUTPUT_FILE_ARG, outputPath));
        }

        // (2) Parse internal test collection, topics & stop words documents...
        List<InfoDocument> parsedDocuments = (List<InfoDocument>) loadFileEntries(TREC_DATASET, TRECTools::parseCollection);
        List<Query> parsedQueries = (List<Query>) loadFileEntries(TREC_QUERIES, TRECTools::parseQueries);
        Set<String> stopWords = (Set<String>) loadFileEntries(STOP_WORDS, TRECTools::parseStopWords);

        MicroblogTokenizer tokenizer = new MicroblogTokenizer();
        InvertedIndex index = new InvertedIndex(parsedDocuments.size(), tokenizer);

        /// (3) Build Inverted Index
        for (InfoDocument document : parsedDocuments) {
            String[] tokens = tokenizer.tokenizeDocument(document.getDocument());
            for (String word : tokens) {
                index.addTerm(word, document.getID());
            }
        }

        // (4) Filter stop words
        index.filterStopWords(stopWords);

        // (5) Generate a TREC results file derived from a list of documents scored against a set of executed queries
        try {
            String decodedDirPath = URLDecoder.decode(outputPath, "UTF-8");
            File directoryObj = new File(outputPath);
            if (!directoryObj.exists() || !directoryObj.isDirectory()) {
                throw new IllegalArgumentException(String.format("The following file cannot be located or isn't a directory: '%s'", decodedDirPath));
            }
            String resultsFilePath = String.format("%s\\%s", decodedDirPath, RESULTS_FILE_NAME);

            try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(resultsFilePath))) {
                for (Query query : parsedQueries) {
                    String[] tokens = tokenizer.tokenizeQuery(query.getQuery());
                    List<Pair<String, Double>> cosineScores = Scoring.cosineScore(query.getID(), tokens, index);

                    for (int i = 0; i < cosineScores.size(); i++) {
                        String docID = cosineScores.get(i).getLeft();
                        double cosineScore = cosineScores.get(i).getRight();

                        fileWriter.write(String.format("%s Q0 %s %d %,.6f myRun%n", query.getID(), docID, i + 1, cosineScore));
                    }
                }
            } catch (IOException ex) {
                throw new IllegalArgumentException(String.format("Failed to write to the following results file path: '%s'. Cause: '%s'", resultsFilePath, ex.getCause().getMessage()));
            }
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(String.format("Failed to decode the following file path: '%s'. Cause: '%s'", outputPath, ex.getCause().getMessage()));
        }
    }
}
