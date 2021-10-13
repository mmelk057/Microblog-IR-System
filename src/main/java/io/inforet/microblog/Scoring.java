package io.inforet.microblog;

import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class Scoring {

    /**
     * Weigh query term using a modified tf-idf weighting scheme for query terms:
     * w_iq = (0.5 + 0.5 tf_iq)∙idf_i
     * @param totalNumberOfDocuments Total # of documents in the collection
     * @param documentFrequency # of documents that the query term appears in
     * @param termFrequency frequency that the query term appears in the query itself
     * @return Weighting for a given query term
     */
    private static double weighQueryTerm(double totalNumberOfDocuments, double documentFrequency, double termFrequency) {
        double weightedTermFrequency = 0.5 + (0.5 * adjustTermFrequency(termFrequency));
        // Calculate the IDF (inverse document frequency),
        // a measurement that bias' towards unique terms
        double inverseDocumentFrequency = Math.log10(totalNumberOfDocuments / documentFrequency);
        return weightedTermFrequency * inverseDocumentFrequency;
    }

    /**
     * Dampens the effect of the term frequency using a logarithm scheme
     * @param termFrequency Term frequency to adjust
     * @return Dampened term frequency
     */
    private static double adjustTermFrequency (double termFrequency) {
        if (termFrequency > 0) {
            return 1 + Math.log10(termFrequency);
        }
        return 0;
    }

    /**
     * Calculates the cosine similarity score of the query with every document
     * in the inverted index that at least one of the query terms appears in
     * @param invertedIndex an inverted index
     * @param queryTerms a query
     * @return A list of cosine scores for the query
     */
    public static List<Pair<String, Double>> cosineScore(String queryID, String[] queryTerms, InvertedIndex invertedIndex) {

        HashMap<String, Double> cosineScores = new HashMap<>();
        HashMap<String, Double> documentLengths = new HashMap<>();

        // Break query terms up into their respective term frequencies within the query
        HashMap<String, Double> termFrequencyHashMap = getTermFrequencyHashMap(queryTerms);
        double maxTermFrequency = getMaxTermFrequency(termFrequencyHashMap);

        // calculate weights while keeping track of vector distances
        int totalNumberOfDocuments = invertedIndex.getTotalNumberOfDocuments();
        for (String queryTerm: queryTerms) {
            // GET all the documents from the index where the query term is found
            Map<String, Integer> documentList = invertedIndex.getDocumentList(queryTerm);
            if (documentList == null || documentList.isEmpty()) {
                // term is irrelevant to the score...
                continue;
            }
            double termFrequency = termFrequencyHashMap.get(queryTerm);

            // Further dampen the query term by multiplying it with the inverse of the maximum term frequency in the query
            double unnormalizedTermWeight = maxTermFrequency * weighQueryTerm(totalNumberOfDocuments, documentList.size(), termFrequency);
            // ACCUMULATE THE QUERY TERM EUCLIDEAN LENGTH COMPONENTS (USED FOR NORMALIZATION!)
            if(!documentLengths.containsKey(queryID)) {
                documentLengths.put(queryID, Math.pow(unnormalizedTermWeight, 2));
            } else {
                Double oldVal = documentLengths.get(queryID);
                documentLengths.replace(queryID, oldVal + Math.pow(unnormalizedTermWeight, 2));
            }

            // Scan through documents associated with the query term
            for (Map.Entry<String, Integer> documentEntry : documentList.entrySet()) {

                double unnormalizedDocumentTermWeight = adjustTermFrequency(documentEntry.getValue());
                // ACCUMULATE DOCUMENT TERM EUCLIDEAN LENGTH COMPONENTS (USED FOR NORMALIZATION)
                if (!documentLengths.containsKey(documentEntry.getKey())) {
                    documentLengths.put(documentEntry.getKey(), Math.pow(unnormalizedDocumentTermWeight, 2));
                } else {
                    Double oldVal = documentLengths.get(documentEntry.getKey());
                    documentLengths.replace(documentEntry.getKey(), oldVal + Math.pow(unnormalizedDocumentTermWeight, 2));
                }

                double cosineScoreComponent = unnormalizedTermWeight * unnormalizedDocumentTermWeight;
                // ACCUMULATE QUERY-DOCUMENT SIMILARITY SCORES
                if (!cosineScores.containsKey(documentEntry.getKey())) {
                    cosineScores.put(documentEntry.getKey(), cosineScoreComponent);
                } else {
                    Double oldVal = cosineScores.get(documentEntry.getKey());
                    cosineScores.replace(documentEntry.getKey(), oldVal + cosineScoreComponent);
                }

            }

        }

        // (1) NORMALIZE WEIGHTS
        double queryEuclideanLength = Math.sqrt(documentLengths.get(queryID));
        for (Map.Entry<String, Double> documentLength : documentLengths.entrySet()) {
            double documentEuclideanLength = Math.sqrt(documentLength.getValue());
            // |V(q)| * |V(d)|
            documentLengths.replace(documentLength.getKey(), queryEuclideanLength * documentEuclideanLength);
        }

        // (2) CALCULATE COSINE SIMILARITY SCORE
        List<Pair<String, Double>> normalizedCosineScores = new ArrayList<>();
        for (Map.Entry<String, Double> cosineScore : cosineScores.entrySet()) {
            //   V(q) * V(d)
            // ---------------
            // |V(q)| * |V(d)|
            double normalizationFactor = documentLengths.get(cosineScore.getKey());
            normalizedCosineScores.add(Pair.of(cosineScore.getKey(),cosineScore.getValue() /  normalizationFactor));
        }

        // (3) SORT normalized cosine scores by descending value
        normalizedCosineScores.sort((o1, o2) -> {
            if (o1.getRight() > o2.getRight()) {
                return -1;
            } else if (o1.getRight().equals(o2.getRight())) {
                return 0;
            } else {
                return 1;
            }
        });

        return normalizedCosineScores;
    }

    /**
     * Calculate the frequency of every term in the query
     * @param queryTerms a query
     * @return A HashMap mapping query terms to their frequency
     */
    private static HashMap<String, Double> getTermFrequencyHashMap(String[] queryTerms) {
        HashMap<String, Double> termCount = new HashMap<>(queryTerms.length);
        for (String queryTerm : queryTerms) {
            Double count = termCount.get(queryTerm);
            if (count == null) {
                termCount.put(queryTerm, 1d);
            } else {
                termCount.put(queryTerm, count + 1);
            }
        }
        return termCount;
    }

    /**
     * Calculate the maximum term frequency in the HashMap
     * @param termCount a HashMap mapping query terms to their frequency
     * @return The maximum term frequency in the HashMap
     */
    private static double getMaxTermFrequency(HashMap<String, Double> termCount) {
        double maxTermFrequency = 0;

        for (Double termFrequency : termCount.values()) {
            if (termFrequency > maxTermFrequency) {
                maxTermFrequency = termFrequency;
            }
        }

        return maxTermFrequency;
    }

}
