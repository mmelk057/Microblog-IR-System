package io.inforet.microblog;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class DocsAndTF{
    /**
     * DocsAndTF is a class containing 2 instance variables : docs and docFrequency
     * docs is a hashmap with the key as a document ID and the value as the term frequency
     * docFrequency is the the number of document IDs present in the hashmap
     */

    private HashMap<String, Integer> docs;
    private Integer docFrequency;


    public DocsAndTF(HashMap<String, Integer> docs){
        this.docs = docs;
        docFrequency = 1;
    }
    
    /**
     * Adds the document ID to the hashmap if the latter is not already there
     * Or increments the corresponding term frequency 
     * @param document Thw document ID to be added
     */
    public void addDoc(String document){
        if (docs.containsKey(document)){
            Integer value = docs.get(document);
            value += 1;
            docs.put(document,value);
        }else{
            docs.put(document,1);
            docFrequency = docFrequency + 1;
        }
    }

    

    public Integer getTermFrequency(String document){
        return (docs.get(document));
    }

    public Integer getDocumentFrequency(){
        return docFrequency;
    }

    public HashMap<String,Integer> getDocuments(){
        return docs;
    }

    /**
     * Prints the hashmap associated with a word
     */

    public void printDocumentsAndTF(){
        Set entrySet = docs.entrySet();
        Iterator it = entrySet.iterator();
        while(it.hasNext()){
            Map.Entry me = (Map.Entry)it.next();
            System.out.println("Key is " + me.getKey() + " value is " + me.getValue());
        }

    }


}