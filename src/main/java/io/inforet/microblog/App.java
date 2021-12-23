package io.inforet.microblog;

import io.inforet.microblog.entities.InfoDocument;
import io.inforet.microblog.entities.Query;
import io.inforet.microblog.tokenization.MicroblogTokenizer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.deeplearning4j.models.word2vec.Word2Vec;

import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;
import java.util.stream.Collectors;

public class App {

    /**
     * JVM Argument Key.
     * Used to specify the absolute file system directory to output the TREC results file.
     */
    public static final String OUTPUT_FILE_ARG = "resultsDir";

    /**
     * Name of the TREC results file to output, once each query has been processed.
     */
    public static final String RESULTS_FILE_NAME = "Results.txt";

    /**
     * Principal dataset containing a collection of tweets, dating back to TREC's 2011 Microblog Track.
     * SOURCE: https://trec.nist.gov/data/microblog2011.html
     */
    public static final String TREC_DATASET = "/trec-dataset.txt";

    /**
     * Principal list of queries, dating back to TREC's 2011 Microblog Track.
     * SOURCE: https://trec.nist.gov/data/microblog2011.html
     */
    public static final String TREC_QUERIES = "/trec-queries.xml";

    /**
     * List of stop words to filter (common words with little distinguishable properties)
     */
    public static final String STOP_WORDS = "/stop-words.txt";

    /**
     * News commentary corpus containing 180K+ lines of EN commentary.
     * SOURCE: http://www.statmt.org/wmt11/translation-task.html#download
     *
     * The intent is to use this, or another external dataset of sorts, to train a neural network via word embeddings
     */
    public static final String WORD2VEC_TRAINING_DATASET = "/models/news-commentary-v6.en";

    /**
     * Maximum threshold of documents to return for each query
     */
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
        for (int i = 0; i < parsedDocuments.size(); i++) {

            InfoDocument selectedDoc = parsedDocuments.get(i);

            app.adjustDocumentFactor(documentWeightFactor, selectedDoc.getID(), selectedDoc.getDocument());

            // TOKENIZE DOCUMENT!
            String[] tokens = tokenizer.tokenizeDocument(selectedDoc.getDocument());

            // The tokenization process filtered any special characters, canonicalized URLs, performed various word expansions & linguistic morphologies, etc.
            // Coalesce the outputted, SANITIZED, tokens and replace the old raw TREC content
            parsedDocuments.set(i, new InfoDocument(selectedDoc.getID(), String.join(StringUtils.SPACE, tokens)));

            // Adjust the weight factor for a given document based on internal criteria (i.e., casing, special char usage,...)
            app.adjustDocumentFactor(documentWeightFactor, selectedDoc.getID(), tokens);

            for (String word : tokens) {
                invertedIndex.addTerm(word, selectedDoc.getID());
            }
        }

        // Mapping of document IDs towards their associated content!
        Map<String, InfoDocument> parsedDocumentMap = parsedDocuments.stream().collect(Collectors.toMap(InfoDocument::getID, Function.identity()));

        // (4) BUILD Word2Vector neural net model!
        // DOCUMENTATION: https://deeplearning4j.konduit.ai/deeplearning4j/reference/word2vec-glove-doc2vec
        Word2Vec w2vModel = MLTools.buildWord2VecModel(parsedDocuments);
        /**
         * If you wish to build a word2vector model using an external dataset, here's the appropriate function:
         *
         * Word2Vec w2vModel = MLTools.buildWord2VecModel(WORD2VEC_TRAINING_DATASET);
         */

        // (5) Filter stop words
        invertedIndex.filterStopWords(stopWords);

        // (6) Sort parsed queries by id and then reformat the id
        parsedQueries.sort(app.ALPHABETICAL_ORDER);
        for (int i = 0; i < parsedQueries.size(); i++) {
            parsedQueries.get(i).setID(String.valueOf(i + 1));      // reformat the query id to match the formatting in the evaluation file
        }

        // (7) Generate a TREC results file derived from a list of documents scored against a set of executed queries
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
                    // Content-based cosine similarity (purely syntactic) scores for each document against a given query!
                    List<Pair<String, Double>> cosineScores = scoringInst.cosineScore(query, invertedIndex, documentWeightFactor);

                    // Limit the query return size
                    int queryReturnSize = cosineScores.size();
                    if (queryReturnSize > MAX_QUERY_RETURN_SIZE) queryReturnSize = MAX_QUERY_RETURN_SIZE;


                    /**
                     * Strategy:
                     * Re-rank the top 'MAX_QUERY_RETURN_SIZE' documents derived from the content-based cosine similarity score (purely syntactic).
                     * The re-ranking will be predicated on a scoring that will consist of a linear combination between:
                     * 1. The content-based cosine similarity score
                     * 2. Co-occurrence similarity average score (derived from the Word2Vector model)
                     *
                     * Score = [α * COSINE_SCORE] + [(1 - α) * CO-OCCURRENCE_SCORE]
                     * Where 'α' (alpha), is a modulating constant
                     */
                    double scoringAlpha = 0.73;
                    // List of (document ids -> final scoring)
                    List<Pair<String, Double>> finalScoring = Collections.synchronizedList(new ArrayList<>());

                    /**
                     * To calculate the co-occurrence similarity average score, we will essentially
                     * compare the similarity of each token from the query, against each token for all the top 1000 returned documents.
                     * i.e.,
                     * Query = "BBC World News"
                     * Document = "BBC farms are opening worldwide in the news"
                     * Score =
                     *           ( ( [sim("BBC", "BBC") + sim("BBC", "farms") + ... + sim("BBC", "news")] / |Document| ) +
                     *             ( [sim("World", "farms") + ... + sim("World", "news") ] / |Document| ) +
                     *             ...
                     *           ) / |Query|
                     *
                     * The intuition here is to rank documents containing lots of dissimilar terms that are not-commonly seen together,
                     * lower! Vice-versa
                     *
                     * So in our example, it may be the case that "farms" is a very uncommon word, not commonly associated with
                     * "BBC", "World", or "News", and therefore, the document as a whole is ranked lower.
                     *
                     * This is a fairly expensive operation to perform, so we use threading to parallelize the computations.
                     */
                    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
                    Collection<Future<?>> futures = new LinkedList<>();

                    for (int i = 0; i < queryReturnSize; i++) {

                        final int positionIndex = i;
                        // SUBMIT ASYNC TASK TO QUEUE (within Thread pool!)
                        futures.add(executor.submit(() -> {

                            String docID = cosineScores.get(positionIndex).getLeft();
                            double cosineScore = cosineScores.get(positionIndex).getRight();

                            double cooccurenceScore = 0d;
                            // Simple delineation to fetch tokens!
                            String[] queryComponents = query.getQuery().toLowerCase(Locale.ROOT).split(StringUtils.SPACE);
                            String[] documentComponents = parsedDocumentMap.get(docID).getDocument().toLowerCase(Locale.ROOT).split(StringUtils.SPACE);

                            for (String queryTerm : queryComponents){
                                double currentScore = -1d;
                                for (String documentTerm : documentComponents) {
                                    // IGNORE STOP WORDS!!
                                    if (stopWords.contains(documentTerm)) {
                                        currentScore -= 0.15;
                                        continue;
                                    }

                                    // Co-occurrence is calculated using our trained Word2Vector model (uses word embeddings!)
                                    double cooccurenceSimilarity = w2vModel.similarity(queryTerm, documentTerm);
                                    if (!Double.isNaN(cooccurenceSimilarity)) {
                                        currentScore += cooccurenceSimilarity;
                                    } else {
                                        currentScore -= 0.1;
                                    }
                                }
                                if (documentComponents.length > 0) {
                                    currentScore /= documentComponents.length;
                                }
                                cooccurenceScore += currentScore;
                            }
                            cooccurenceScore /= queryComponents.length;

                            // Linear combination scoring system!
                            finalScoring.add(new ImmutablePair<>(docID, (scoringAlpha * cosineScore) + ((1 - scoringAlpha) * cooccurenceScore)));
                        }));
                    }

                    // Wait for all tasks to finish...
                    for (Future<?> future: futures) {
                        try {
                            future.get();
                        } catch (InterruptedException ex){
                            System.out.printf("Thread has been interrupted! Reason: %s", ex.getMessage());
                        }
                        catch (ExecutionException ex) {
                            System.out.printf("Thread has been faulted! Reason: %s", ex.getMessage());
                        }
                    }

                    executor.shutdown();

                    // Re-rank query results!
                    // Highest (TOP) -> Lowest (BOTTOM)
                    finalScoring.sort((o1, o2) -> {
                        if (o1.getRight() > o2.getRight()) {
                            return -1;
                        } else if (o1.getRight().equals(o2.getRight())) {
                            return 0;
                        }
                        return 1;
                    });

                    // OUTPUT!
                    for (int i = 0; i < finalScoring.size(); i++) {
                        Pair<String, Double> documentHead = finalScoring.get(i);
                        fileWriter.write(String.format("%s Q0 %s %d %.6f myRun\n",
                                query.getID(),
                                documentHead.getLeft(),
                                i + 1,
                                documentHead.getRight()));
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
