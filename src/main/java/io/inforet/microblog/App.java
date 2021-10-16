package io.inforet.microblog;

import io.inforet.microblog.entities.InfoDocument;
import io.inforet.microblog.entities.Query;
import io.inforet.microblog.tokenization.MicroblogTokenizer;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

public class App {

    public static final String OUTPUT_FILE_ARG = "resultsDir";
    public static final String RESULTS_FILE_NAME = "Results.txt";
    public static final String TREC_DATASET = "/trec-dataset.txt";
    public static final String TREC_QUERIES = "/trec-queries.xml";
    public static final String STOP_WORDS = "/stop-words.txt";
    public static final int MAX_QUERY_RETURN_SIZE = 1000;

    /**
     * Parses a generic list of entries from a given fileName
     * @param fileName Relative path
     * @param fileCallback Callback to invoke, providing the fileName, with the expectation of a generic list of entries
     * @param <T> Entry type
     * @return Generic list of entries
     */
    public <T> Collection<T> loadFileEntries(String fileName, Function<InputStream, Collection<T>> fileCallback) {
        InputStream datasetURL = getClass().getResourceAsStream(fileName);
        if (datasetURL == null) {
            throw new IllegalArgumentException(String.format("Failed to find the following file: '%s'", fileName));
        }
        return fileCallback.apply(datasetURL);
    }

    /**
     * Sorts a list of queries by query id
     * @param o1 a query
     * @param o2 another query
     * @return A sorted list of queries
     */
    public Comparator<Query> ALPHABETICAL_ORDER = (o1, o2) -> {
        int result = String.CASE_INSENSITIVE_ORDER.compare(o1.getID(), o2.getID());
        if (result == 0) {
            result = o1.getID().compareTo(o2.getID());
        }
        return result;

    };

    /**
     * Adjusts the document factor weight based on internal criteria like casing, special character usage, etc.
     * 0 <= value <= 1, where 1 is the highest.
     * @param documentFactors Mapping of document IDs to their corresponding factors
     * @param docID Document ID
     * @param tokens List of tokens corresponding to the provided document ID
     */
    public void adjustDocumentFactor(Map<String, Double> documentFactors, String docID, String[] tokens) {
        double weightFactor = 1.0d;
        // '?' character weighting...
        long qMarkCount = Arrays.stream(tokens).filter(token -> token.equals("?")).count();
        weightFactor *= Math.pow(0.87, qMarkCount);

        // '@' character weighting (indicates getting another user's attention)
        // SYSTEM DOESN'T IMPROVE
//        long mentionCount = Arrays.stream(tokens).filter(token -> token.startsWith("@")).count();
//        weightFactor *= Math.pow(0.89, mentionCount);

//        // '#' character weighting (indicates broadcasting a message)
        // SYSTEM DOESN'T IMPROVE
//        long hashtagCount = Arrays.stream(tokens).filter(token -> token.startsWith("#")).count();
//        weightFactor *= Math.pow(1.01, hashtagCount);

        // ADD ENTRY
        if (!documentFactors.containsKey(docID)) {
            documentFactors.put(docID, Math.min(weightFactor, 1d));
        } else {
            Double oldVal = documentFactors.get(docID);
            documentFactors.replace(docID, Math.min(oldVal * weightFactor, 1d));
        }
    }

    /**
     * Adjusts the document factor weight based on internal criteria like casing, special character usage, etc.
     * 0 <= value <= 1, where 1 is the highest.
     * @param documentFactors Mapping of document IDs to their corresponding factors
     * @param docID Document ID
     * @param rawDocument Raw document content
     */
    public void adjustDocumentFactor(Map<String, Double> documentFactors, String docID, String rawDocument) {
        double weightFactor = 1.0d;
        // Special character weighting...
        char[] rawCharacters = rawDocument.toCharArray();
        int invalidCharCount = 0;
        for (char rawCharacter: rawCharacters) {
            // DOCS: https://www.fileformat.info/info/charset/UTF-8/list.htm
            if (rawCharacter != ' ' &&
                    ( rawCharacter < 33 || rawCharacter > 126 ) ) {
                invalidCharCount++;
            }
        }
        // Each invalid special character found has a 0.98 weighting factor
        weightFactor = invalidCharCount != 0 ? weightFactor * Math.pow(0.99, invalidCharCount) : weightFactor;

        // ADD ENTRY
        if (!documentFactors.containsKey(docID)) {
            documentFactors.put(docID, Math.min(weightFactor, 1d));
        } else {
            Double oldVal = documentFactors.get(docID);
            documentFactors.replace(docID, Math.min(oldVal * weightFactor, 1d));
        }
    }

    public static void main(String[] args) {
        App app = new App();

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
        List<InfoDocument> parsedDocuments = (List<InfoDocument>) app.loadFileEntries(TREC_DATASET, TRECTools::parseCollection);
        List<Query> parsedQueries = (List<Query>) app.loadFileEntries(TREC_QUERIES, TRECTools::parseQueries);
        Set<String> stopWords = (Set<String>) app.loadFileEntries(STOP_WORDS, TRECTools::parseStopWords);

        MicroblogTokenizer tokenizer = new MicroblogTokenizer();
        InvertedIndex invertedIndex = new InvertedIndex(parsedDocuments.size(), tokenizer);

        /// (3) Build Inverted Index & DocumentWeightFactor Index
        Map<String, Double> documentWeightFactor = new HashMap<>();
        for (InfoDocument document : parsedDocuments) {

            app.adjustDocumentFactor(documentWeightFactor, document.getID(), document.getDocument());

            String[] tokens = tokenizer.tokenizeDocument(document.getDocument());

            // Adjust the weight factor for a given document based on internal criteria (i.e., casing, special char usage,...)
            app.adjustDocumentFactor(documentWeightFactor, document.getID(), tokens);

            for (String word : tokens) {
                invertedIndex.addTerm(word, document.getID());
            }
        }

        // (4) Filter stop words
        invertedIndex.filterStopWords(stopWords);

        // (5) Sort parsed queries by id and then reformat the id
        parsedQueries.sort(app.ALPHABETICAL_ORDER);
        for (int i = 0; i < parsedQueries.size(); i++) {
            parsedQueries.get(i).setID(String.valueOf(i + 1));      // reformat the query id to match the formatting in the evaluation file
        }

        // (6) Generate a TREC results file derived from a list of documents scored against a set of executed queries
        try {
            String decodedDirPath = URLDecoder.decode(outputPath, "UTF-8");
            File directoryObj = new File(outputPath);
            if (!directoryObj.exists() || !directoryObj.isDirectory()) {
                throw new IllegalArgumentException(String.format("The following file cannot be located or isn't a directory: '%s'", decodedDirPath));
            }
            String resultsFilePath = String.format("%s\\%s", decodedDirPath, RESULTS_FILE_NAME);

            try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(resultsFilePath))) {
                for (Query query : parsedQueries) {

                    Scoring scoringInst = new Scoring(tokenizer);
                    List<Pair<String, Double>> cosineScores = scoringInst.cosineScore(query, invertedIndex, documentWeightFactor);

                    // Limit the query return size
                    int queryReturnSize = cosineScores.size();
                    if (queryReturnSize > MAX_QUERY_RETURN_SIZE) queryReturnSize = MAX_QUERY_RETURN_SIZE;

                    for (int i = 0; i < queryReturnSize; i++) {
                        String docID = cosineScores.get(i).getLeft();
                        double cosineScore = cosineScores.get(i).getRight();

                        fileWriter.write(String.format("%s Q0 %s %d %.6f myRun\n",
                                query.getID(),
                                docID,
                                i + 1,
                                cosineScore));
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
