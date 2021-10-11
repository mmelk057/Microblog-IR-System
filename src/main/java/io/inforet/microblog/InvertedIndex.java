package io.inforet.microblog;
import java.util.HashMap;


public class InvertedIndex{

    /**
     * The inverted index is a hashmap with the key being a word(string) and the value being of DocsAndTF type
     */
    private HashMap<String,DocsAndTF> index;

    public InvertedIndex(){
        index = new HashMap<String, DocsAndTF>();
    }

    /**
     * Add a new token(word) in the index 
     * It Checks if the token is already in the index
     * If yes, it will add the document id to the hashmap or it will increase the term frequency of that document 
     * If no, it will add the word to the index
     * @param word The word to be added to the index
     * @param document The document ID 
     */
    public void addToken (String word, String document){
        if (index.containsKey(word)){
            DocsAndTF value = index.get(word);
            value.addDoc(document);
        }else{
            HashMap<String, Integer> mp = new HashMap<String, Integer>();
            mp.put(document,1);
            index.put(word, new DocsAndTF(mp));    
        }
    }

    /**
     * Get the term frequency of a particular word in a particular document
     * @param word The word in the index
     * @param document The documentID
     * @return the term frequency
     */
    public Integer getTermFrequency(String word, String document){
        if(index.containsKey(word)){
            DocsAndTF value = index.get(word);
            return value.getTermFrequency(document);
        }else{
            return 0;
        }
    }

    /**
     * Get the document frequency of a word in the index
     * @param word The word in the index
     * @return The document frequency of a word
     */
    public Integer getDocumentFrequency(String word){
        if(index.containsKey(word)){
            DocsAndTF value = index.get(word);
            return value.getDocumentFrequency();
        }else{
            return 0;
        }
    }

    /**
     * Prints the hashmap associated with a word
     * Prints all the documents where the word appears with the corresponding term frequency
     */
    public void getDocumentList(String word){
        if(index.containsKey(word)){
            DocsAndTF value = index.get(word);
            value.printDocumentsAndTF();        
        }else{
            System.out.println("No such word in index");
        }
    }
    

    
}