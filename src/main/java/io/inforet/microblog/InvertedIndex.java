package io.inforet.microblog;
import java.util.HashMap;
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
    private Map<String, Map<String, Integer>> index;
    private final Set<String> stopWords;
    private final int totalNumberOfDocuments;

    public InvertedIndex(int totalNumberOfDocuments, Set<String> stopWords){
        index = new HashMap<>();
        this.stopWords = stopWords;
        this.totalNumberOfDocuments = totalNumberOfDocuments;
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
            return index.get(term).get(docID);
        }
        return 0;
    }

    /**
     * Get the document frequency of a term in the index
     * @param term Term in the index
     * @return The document frequency of a term
     */
    public int getDocumentFrequency(String term) {
        if (index.containsKey(term)) {
            // SIZE OF THE TERM'S DOC LIST
            return index.get(term).size();
        }
        return 0;
    }

    /**
     * Get the document list for a given term
     * @param term Term to fetch document list for
     * @return Mapping of documents to their associated term frequencies
     */
    public Map<String, Integer> getDocumentList(String term) {
        return index.get(term);
    }

    public int getTotalNumberOfDocuments() {
        return totalNumberOfDocuments;
    }
}