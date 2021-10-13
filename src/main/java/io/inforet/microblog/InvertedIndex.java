package io.inforet.microblog;
import io.inforet.microblog.tokenization.MicroblogTokenizer;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.stemmer.Stemmer;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


public class InvertedIndex{

    /**
     * Mapping of terms to a mapping of document IDs to their corresponding term frequency of the
     * term in which they are mapped by.
     * { TERM_1 => { DOC_1 => TF, DOC_2 => TD, ...  }, ... }
     *
     * i.e.,
     * DOC_1: I went to the beach today to get some sun at the beach
     * DOC_2: The beach is very fun. I love going to the beach when the beach is warm
     * { "beach" => { DOC_1 => 2, DOC_2 => 3 } }
     *
     *
     */
    private MicroblogTokenizer tokenizer;
    private Map<String, Map<String, Integer>> index;
    private final int totalNumberOfDocuments;

    public InvertedIndex(int totalNumberOfDocuments, MicroblogTokenizer tokenizer){
        index = new HashMap<>();
        this.totalNumberOfDocuments = totalNumberOfDocuments;
        this.tokenizer = tokenizer;
    }

    /**
     * Linguistically morph a term into various forms & permutations
     * @param term Term to pre-process
     * @return A list of term variations
     */
    private Collection<String> normalize(String term) {
        Set<String> variations = new LinkedHashSet<>();
        List<String> rootTerms = new LinkedList<>();
        // Original...
        rootTerms.add(term);
        // Stemming...
        Stemmer stemmer = new PorterStemmer();
        String stemmed = stemmer.stem(term).toString();
        rootTerms.add(stemmed);
        // TODO: Lemmatize...

        for (String rootTerm : rootTerms) {
            variations.add(rootTerm);
            variations.add(StringUtils.capitalize(rootTerm.toLowerCase(Locale.ROOT)));
            variations.add(rootTerm.toLowerCase(Locale.ROOT));
            variations.add(rootTerm.toUpperCase(Locale.ROOT));
        }
        return variations;
    }

    /**
     * Filters an existing index by a collection of stop words
     * @param stopWords List of stop words
     */
    public void filterStopWords(Collection<String> stopWords) {
        for (String stopWord: stopWords) {
            for (String stopWordVariation : normalize(stopWord)) {
                index.remove(stopWordVariation);
            }
        }
    }

    /**
     * Add a new term in the index
     * Checks if the term exists in the index
     * If YES, increase the term frequency of that document
     * If NO, it will add the word to the index
     * @param term The term to be added to the index
     * @param docID The document ID
     */
    public void addTerm (String term, String docID){
        // CASE: TERM DOESN'T EXIST -> create a new document list for the term with a single entry: the provided docID
        if (!index.containsKey(term)) {
            Map<String, Integer> docList = new HashMap<>();
            docList.put(docID, 1);
            index.put(term, docList);
        }
        // CASE: TERM EXISTS, BUT DOCUMENT DOES NOT (UNDER THE TERM..). ADD AN ENTRY WITHIN THE DOCUMENT LIST!
        else if (!index.get(term).containsKey(docID)) {
            index.get(term).put(docID, 1);
        }
        // CASE: TERM EXISTS, DOCUMENT EXISTS. INCREMENT TERM FREQUENCY!
        else {
            Integer oldVal = index.get(term).get(docID);
            index.get(term).replace(docID, oldVal + 1);
        }
    }

    /**
     * Get the term frequency of a document
     * @param term Term in the index
     * @param docID The document ID
     * @return Term frequency of the specified document
     */
    public int getTermFrequency(String term, String docID) {
        if (index.containsKey(term)) {
            return getDocumentList(term).get(docID);
        }
        return 0;
    }

    /**
     * Get the document frequency of a term in the index
     * @param term Term in the index
     * @return The document frequency of a term
     */
    public int getDocumentFrequency(String term) {
        return getDocumentList(term).size();
    }

    /**
     * Get the document list for a given term AND all its variations
     * @param term Term to fetch document list for
     * @return Mapping of documents to their associated term frequencies
     */
    public Map<String, Integer> getDocumentList(String term) {
        Map<String, Integer> aggregatedDocumentsList = new LinkedHashMap<>();
        for(String termVariation : normalize(term)) {
            if (index.containsKey(termVariation)) {
                Map<String, Integer> currentTermDocList = index.get(termVariation);
                for (Map.Entry<String, Integer> termDocument : currentTermDocList.entrySet()) {
                    if (!aggregatedDocumentsList.containsKey(termDocument.getKey())) {
                        aggregatedDocumentsList.put(termDocument.getKey(), termDocument.getValue());
                    } else {
                        Integer oldDocumentFreq = aggregatedDocumentsList.get(termDocument.getKey());
                        // Basically, all terms variations are ASSUMED to be umbrellaed under a single term.
                        // (i.e., 'Stop' vs 'stop' are the same term...)
                        // If a document exists under another variation with a given document frequency,
                        // simply this variation's document frequency to it.
                        aggregatedDocumentsList.replace(termDocument.getKey(), oldDocumentFreq + termDocument.getValue());
                    }
                }
            }
        }
        return aggregatedDocumentsList;
    }

    public int getTotalNumberOfDocuments() {
        return totalNumberOfDocuments;
    }
}