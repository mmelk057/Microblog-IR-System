package io.inforet.microblog;

import io.inforet.microblog.entities.Query;
import io.inforet.microblog.tokenization.MicroblogTokenizer;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class Scoring {

    private final MicroblogTokenizer tokenizer;

    public Scoring(MicroblogTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    /**
     * Weigh query term using a modified tf-idf weighting scheme for query terms:
     * w_iq = (0.5 + 0.5 tf_iq)∙idf_i
     * @param totalNumberOfDocuments Total # of documents in the collection
     * @param documentFrequency # of documents that the query term appears in
     * @param termFrequency frequency that the query term appears in the query itself
     * @return Weighting for a given query term
     */
    private double weighQueryTerm(double totalNumberOfDocuments, double documentFrequency, double termFrequency) {
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
    private double adjustTermFrequency (double termFrequency) {
        if (termFrequency > 0) {
            return 1 + Math.log10(termFrequency);
        }
        return 0;
    }

    /**
     * Tokenize a provided query and assign a weight to each term where
     * 0 <= weight <= 1.
     * @param query Query to parse
     * @param expansionWeightFactor A query term may be expanded to several linguistic forms - this value specifies
     *                              the factor in which the derived linguistic forms should be weighed, relative
     *                              to their root form ( 0 <= value <= 1 )
     * @return List of weighted query terms
     */
    private Map<String, Double> assignQueryTermWeights(String query, double expansionWeightFactor) {
        Map<String, Double> weightedQueryTerms = new LinkedHashMap<>();
        String[] preliminaryTokens = tokenizer.tokenizeDocument(query);

        // ASSIGN WEIGHTS TO PRELIMINARY TOKENS
        for (String token: preliminaryTokens) {
            // EQUAL WEIGHT!
            weightedQueryTerms.put(token, 1d);
        }

        // APPLY QUERY EXPANSION!
        for(String prelimToken : preliminaryTokens) {
            for (String normalizedForm: tokenizer.normalize(prelimToken)) {
                weightedQueryTerms.computeIfAbsent(normalizedForm, term -> weightedQueryTerms.get(prelimToken) * expansionWeightFactor);
            }
        }

        return weightedQueryTerms;
    }


    public List<Pair<String, Double>> cosineScore(Query query, InvertedIndex invertedIndex) {

        Map<String, Double> queryTermWeights = assignQueryTermWeights(query.getQuery(), 0.75);

        HashMap<String, Double> cosineScores = new HashMap<>();
        HashMap<String, Double> documentLengths = new HashMap<>();

        // Break query terms up into their respective term frequencies within the query
        HashMap<String, Double> termFrequencyHashMap = getTermFrequencyHashMap(queryTermWeights.keySet().toArray(new String[0]));

        // calculate weights while keeping track of vector distances
        int totalNumberOfDocuments = invertedIndex.getTotalNumberOfDocuments();

        for (Map.Entry<String, Double> queryTermWeighted :  queryTermWeights.entrySet()) {
            // GET all the documents from the index where the query term is found
            Map<String, Integer> documentList = invertedIndex.getDocumentList(queryTermWeighted.getKey());
            if (documentList == null || documentList.isEmpty()) {
                // term is irrelevant to the score...
                continue;
            }
            double termFrequency = termFrequencyHashMap.get(queryTermWeighted.getKey());
            // STRATEGY:
            // Each query term has a different weight. Proper nouns are deemed as first-class citizens - their normalized forms as well.
            // Prepositions, articles, common nouns are deemed as less important -> they are secondary to the information need.
            double unnormalizedTermWeight = queryTermWeighted.getValue() * weighQueryTerm(totalNumberOfDocuments, documentList.size(), termFrequency);

            // ACCUMULATE THE QUERY TERM EUCLIDEAN LENGTH COMPONENTS (USED FOR NORMALIZATION!)
            if(!documentLengths.containsKey(query.getID())) {
                documentLengths.put(query.getID(), Math.pow(unnormalizedTermWeight, 2));
            } else {
                Double oldVal = documentLengths.get(query.getID());
                documentLengths.replace(query.getID(), oldVal + Math.pow(unnormalizedTermWeight, 2));
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
        double queryEuclideanLength = Math.sqrt(documentLengths.get(query.getID()));
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

    private HashMap<String, Double> getTermFrequencyHashMap(String[] queryTerms) {
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

}
