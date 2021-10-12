package io.inforet.microblog;

import io.inforet.microblog.entities.InfoDocument;
import io.inforet.microblog.entities.Query;
import io.inforet.microblog.tokenization.MicroblogTokenizer;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class App {

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
        List<InfoDocument> parsedDocuments = (List<InfoDocument>) loadFileEntries(TREC_DATASET, TRECTools::parseCollection);
        List<Query> parsedQueries = (List<Query>) loadFileEntries(TREC_QUERIES, TRECTools::parseQueries);
        Set<String> stopWords = (Set<String>) loadFileEntries(STOP_WORDS, TRECTools::parseStopWords);
        MicroblogTokenizer tokenizer = new MicroblogTokenizer();
        int totalNumberOfDocuments = parsedDocuments.size();
        InvertedIndex index = new InvertedIndex(totalNumberOfDocuments);

        for (InfoDocument document: parsedDocuments) {
            String[] tokens = tokenizer.tokenizeDocument(document.getDocument());
            for(String word: tokens){
                //Do not add stopwords to the inverted index
                if(!stopWords.contains(word)){
                    index.addToken(word,document.getID());
                }
            }           
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("Results.txt"))) {
            for (Query query : parsedQueries) {
                String[] tokens = tokenizer.tokenizeDocument(query.getQuery());
                List<Pair<String, Double>> cosineScores = Scoring.cosineScore(index, tokens);

                for (int i = 0; i < cosineScores.size(); i++) {
                    String docID = cosineScores.get(i).getLeft();
                    double cosineScore = cosineScores.get(i).getRight();

                    System.out.printf("%s Q0 %s %d %.6f myRun%n", query.getID(), docID, i + 1, cosineScore);
                    writer.write(String.format("%s Q0 %s %d %.6f myRun%n", query.getID(), docID, i + 1, cosineScore));
                }
            }
        }
        catch (Exception ex) {System.out.println(ex.getMessage());}
    }
    
}
