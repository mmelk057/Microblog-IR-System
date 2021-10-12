package io.inforet.microblog;

import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class Scoring {
    // Note the assignment instructions speak of a modified tf-idf weighting scheme for query terms: w_iq = (0.5 + 0.5 tf_iq)∙idf_i. Perhaps we can test this version later.
    private static double weightQueryTerm(int totalNumberOfDocuments, int documentFrequency, int termFrequency) {
        double weightedTermFrequency = (0.5 + 0.5 * weightTermFrequency(termFrequency));
        double inverseDocumentFrequency = calculateInverseDocumentFrequency(totalNumberOfDocuments, documentFrequency);
        double weight = weightedTermFrequency * inverseDocumentFrequency;
        return weight;
    }

    private static double weightDocumentTerm(int termFrequency) {
        double weightedTermFrequency = weightTermFrequency(termFrequency);
        return weightedTermFrequency;
    }

    private static double weightTermFrequency(double termFrequency) {
        double termFrequencyWeight = 0.0;

        if (termFrequency > 0) {
            termFrequencyWeight = 1 + Math.log10(termFrequency);
        }

        return termFrequencyWeight;
    }

    private static double calculateInverseDocumentFrequency(double totalNumberOfDocuments, int documentFrequency) {
        double inverseDocumentFrequency = Math.log10(totalNumberOfDocuments / documentFrequency);
        return inverseDocumentFrequency;
    }

    public static List<Pair<String, Double>> cosineScore(InvertedIndex invertedIndex, String[] queryTerms) {
        int totalNumberOfDocuments = invertedIndex.getTotalNumberOfDocuments();

        HashMap<String, Double> cosineScores = new HashMap<>();
        HashMap<String, Double> documentLengths = new HashMap<>();
        String queryKey = "QueryVectorDistance";    // an arbitrary key

        HashMap<String, Integer> termFrequencyHashMap = getTermFrequencyHashMap(queryTerms);
        int maxTermFrequency = getMaxTermFrequency(termFrequencyHashMap);

        // calculate weights while keeping track of vector distances
        int queryTermsLength = queryTerms.length;
        for (int i = 0; i < queryTermsLength; i++) {
            String queryTerm = queryTerms[i];

            int documentFrequency = invertedIndex.getDocumentFrequency(queryTerm);
            int termFrequency = termFrequencyHashMap.get(queryTerm) / maxTermFrequency;
            double weightedQueryTerm = weightQueryTerm(totalNumberOfDocuments, documentFrequency, termFrequency);

            double querySum = Math.pow(weightedQueryTerm, 2);
            Double oldQuerySum = documentLengths.get(queryKey);
            if (oldQuerySum == null) {
                documentLengths.put(queryKey, querySum);
            } else {
                documentLengths.put(queryKey, oldQuerySum + querySum);
            }

            DocsAndTF documentList = invertedIndex.getDocumentList(queryTerm);
            if (documentList == null) continue; // do not process terms that do not exist in the inverted index

            for (Map.Entry<String, Integer> entry : documentList.getDocuments().entrySet()) {
                String docID = entry.getKey();

                double weightedDocumentTerm = weightDocumentTerm(entry.getValue());
                double cosineScore = weightedQueryTerm * weightedDocumentTerm;

                double documentLength = Math.pow(weightedDocumentTerm, 2);

                Double oldCosineScore = cosineScores.get(queryTerm);
                Double oldDocumentLength = documentLengths.get(queryTerm);

                if (oldCosineScore == null) {
                    cosineScores.put(docID, cosineScore);
                    documentLengths.put(docID, documentLength);
                } else {
                    cosineScores.put(docID, oldCosineScore + cosineScore);
                    documentLengths.put(docID, oldDocumentLength + documentLength);
                }
            }
        }

        // normalize weights, then calculate cosine similarity score
        for (int i = 0; i < queryTermsLength; i++) {
            String queryTerm = queryTerms[i];
            DocsAndTF documentList = invertedIndex.getDocumentList(queryTerm);
            if (documentList == null) continue; // do not process terms that do not exist in the inverted index

            for (Map.Entry<String, Integer> entry : documentList.getDocuments().entrySet()) {
                String docID = entry.getKey();
                double documentLength = documentLengths.get(docID);
                double queryLength = documentLengths.get(queryKey);
                double cosineScore = cosineScores.get(docID);

                documentLength = Math.sqrt(documentLength);
                queryLength = Math.sqrt(queryLength);
                cosineScore = cosineScore / (documentLength * queryLength);
                cosineScores.put(docID, cosineScore);
            }
        }

        // save the output to an ArrayList
        List<Pair<String, Double>> cosineScoresList = new ArrayList<>();
        for (Map.Entry<String, Double> entry : cosineScores.entrySet()) {
            Pair<String, Double> pair = Pair.of(entry.getKey(), entry.getValue());
            cosineScoresList.add(pair);
        }

        // sort the cosine scores by descending value
        cosineScoresList.sort(new Comparator<Pair<String, Double>>() {
            @Override
            public int compare(Pair<String, Double> o1, Pair<String, Double> o2) {
                if (o1.getRight() > o2.getRight()) {
                    return -1;
                } else if (o1.getRight().equals(o2.getRight())) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });

        return cosineScoresList;
    }

    private static HashMap<String, Integer> getTermFrequencyHashMap(String[] queryTerms) {
        HashMap<String, Integer> termCount = new HashMap<>(queryTerms.length);

        for (String queryTerm : queryTerms) {
            Integer count = termCount.get(queryTerm);

            if (count == null) {
                termCount.put(queryTerm, 1);
            } else {
                termCount.put(queryTerm, ++count);
            }
        }

        return termCount;
    }

    private static int getMaxTermFrequency(HashMap<String, Integer> termCount) {
        int maxTermFrequency = 0;

        for (Integer termFrequency : termCount.values()) {
            if (termFrequency > maxTermFrequency) {
                maxTermFrequency = termFrequency;
            }
        }

        return maxTermFrequency;
    }
}
