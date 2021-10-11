package io.inforet.microblog;

import io.inforet.microblog.entities.InfoDocument;
import io.inforet.microblog.entities.Query;

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
        InvertedIndex index = new InvertedIndex();


        for (InfoDocument document: parsedDocuments) {
            String[] tokens = tokenizer.tokenizeDocument(document.getDocument());
            String[] id = tokenizer.tokenizeDocument(document.getID());
            //get the document id
            String idNumber = id[0];
            for(String word: tokens){
                //Do not add stopwords to the inverted index
                if(!stopWords.contains(word)){
                    index.addToken(word,idNumber);
                }
                
            }           
        }       
    }
    
}
