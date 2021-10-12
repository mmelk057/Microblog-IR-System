package io.inforet.microblog;

import io.inforet.microblog.entities.InfoDocument;
import io.inforet.microblog.entities.Query;
import io.inforet.microblog.entities.RankedDocument;
import io.inforet.microblog.tokenization.MicroblogTokenizer;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class App {

    public static final String OUTPUT_FILE_ARG = "resultsDir";
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
        } catch(InvalidPathException ex) {
            throw new IllegalArgumentException(String.format("The following argument: '-D%s' must be a well-formed directory that exists. Malformed input provided: '%s'", OUTPUT_FILE_ARG, outputPath));
        }

        // (2) Parse internal test collection, topics & stop words documents...
        List<InfoDocument> parsedDocuments = (List<InfoDocument>) loadFileEntries(TREC_DATASET, TRECTools::parseCollection);
        List<Query> parsedQueries = (List<Query>) loadFileEntries(TREC_QUERIES, TRECTools::parseQueries);
        Set<String> stopWords = (Set<String>) loadFileEntries(STOP_WORDS, TRECTools::parseStopWords);
        MicroblogTokenizer tokenizer = new MicroblogTokenizer();
        for (InfoDocument document: parsedDocuments) {
            String[] tokens = tokenizer.tokenizeDocument(document.getDocument());
            /**
             * TODO: Build Inverted Index Structure
             */
        }

        // (4) Generate TREC results file
        // TEST DATA (TEMP)------------------------------
        List<RankedDocument> rankedDocuments = new ArrayList<>();
        rankedDocuments.add(new RankedDocument(new Query("MB001", "EXAMPLE"), new InfoDocument("1234", "SOME CONTENT"), 0.3421));
        rankedDocuments.add(new RankedDocument(new Query("MB020", "EXAMPLE"), new InfoDocument("28932", "SOME CONTENT"), 0.9721));
        rankedDocuments.add(new RankedDocument(new Query("MB001", "EXAMPLE"), new InfoDocument("4567", "SOME CONTENT"), 0.387));
        rankedDocuments.add(new RankedDocument(new Query("MB020", "EXAMPLE"), new InfoDocument("29382", "SOME CONTENT"), 0.9213));
        //-----------------------------------------------
        TRECTools.generateResultsFile(outputPath, rankedDocuments);
    }
}
