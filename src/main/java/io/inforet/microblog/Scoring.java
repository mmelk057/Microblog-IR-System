package io.inforet.microblog;

import io.inforet.microblog.entities.Query;
import io.inforet.microblog.tokenization.MicroblogTokenizer;
import opennlp.tools.util.Span;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

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
        // ASSUMPTION : preliminaryTokens have been adjusted to include named entities
        String[] preliminaryTokens = tokenizer.tokenizeDocument(query);

        // ASSIGN WEIGHTS TO PRELIMINARY TOKENS
        // Since the tokenization process coalesces multi-length tokens already,
        // the following stage is to detect singular proper nouns from our set of tokens
        List<Integer> singleProperNouns = tokenizer.findNamedEntities(preliminaryTokens)
                                                .stream()
                                                .filter(span -> ((span.getEnd() - span.getStart()) == 1))
                                                .map(Span::getStart).collect(Collectors.toList());
        for (int i = 0; i < preliminaryTokens.length; i++) {
            // Proper nouns are weighted 1.2x higher than non-proper nouns
            if (singleProperNouns.contains(i) || preliminaryTokens[i].split(StringUtils.SPACE).length > 1) {
                weightedQueryTerms.put(preliminaryTokens[i], 1.2d);
            } else {
                weightedQueryTerms.put(preliminaryTokens[i], 1d);
            }
        }

        // APPLY QUERY EXPANSION!
        for(String prelimToken : preliminaryTokens) {
            for (String normalizedForm: tokenizer.normalize(prelimToken)) {
                weightedQueryTerms.computeIfAbsent(normalizedForm, term -> weightedQueryTerms.get(prelimToken) * expansionWeightFactor);
            }
        }

        return weightedQueryTerms;
    }

    /**
     * Calculates the cosine similarity score of the query with every document
     * in the inverted index that at least one of the query terms appears in
     * @param query Query to calculate cosine similarity score
     * @param invertedIndex an inverted index
     * @param documentWeights A mapping of document IDs against their respective individual weight factor multipliers.
     *                        This is based on criteria like casing, POS tagging, special character usage, etc.
     *                        0 <= value <= 1
     * @return A list of cosine scores for the query
     */
    public List<Pair<String, Double>> cosineScore(Query query, InvertedIndex invertedIndex, Map<String, Double> documentWeights) {

        Map<String, Double> queryTermWeights = assignQueryTermWeights(query.getQuery(), 0.65);

        HashMap<String, Double> cosineScores = new HashMap<>();
        HashMap<String, Double> documentLengths = new HashMap<>();

        // Break query terms up into their respective term frequencies within the query
        HashMap<String, Double> termFrequencyHashMap = getTermFrequencyHashMap(queryTermWeights.keySet().toArray(new String[0]));
        // Calculate the maximum term frequency in the HashMap
        Optional<Double> maxTermFrequency = termFrequencyHashMap.values().stream().max(Double::compareTo);

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
            // Furthermore, dampen the query term by multiplying it with the inverse of the maximum term frequency in the query
            double unnormalizedTermWeight = (1 / maxTermFrequency.orElseThrow()) *
                                            queryTermWeighted.getValue() *
                                            weighQueryTerm(totalNumberOfDocuments, documentList.size(), termFrequency);

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

            Double documentWeightFactor = documentWeights.getOrDefault(cosineScore.getKey(), 1d);
            assert documentWeightFactor >= 0 && documentWeightFactor <= 1;

            Double finalCosineScore = ( documentWeightFactor * cosineScore.getValue() ) / normalizationFactor;
            normalizedCosineScores.add(Pair.of(cosineScore.getKey(), finalCosineScore));
        }

        // (3) SORT normalized cosine scores by descending value
        normalizedCosineScores.sort((o1, o2) -> {
            if (o1.getRight() > o2.getRight()) {
                return -1;
            } else if (o1.getRight().equals(o2.getRight())) {
                return 0;
            }
            return 1;
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

}
