package io.inforet.microblog;

import io.inforet.microblog.entities.InfoDocument;
import io.inforet.microblog.entities.Query;
import io.inforet.microblog.entities.RankedDocument;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public class TRECTools {

    /**
     * Converts a provided TREC formatted list of documents
     * into a list of Document objects
     * @param filePath Path to external documents to parse
     * @return List of Document objects
     */
    public static List<InfoDocument> parseCollection(String filePath) {
        List<InfoDocument> parsedCollection = new LinkedList<>();
        try {
            String decodedFilePath = URLDecoder.decode(filePath, "UTF-8");
            try (BufferedReader fileReader = new BufferedReader(new FileReader(decodedFilePath))) {
                String currLine;
                while ((currLine = fileReader.readLine()) != null) {
                    // TREC document collections specify TWO segments, tab delimited!
                    String[] segments = currLine.split("\t");
                    assert segments.length == 2;
                    // Numeric ID!
                    String filteredID = StringUtils.stripToEmpty(segments[0]).replaceAll("[^0-9]", "");
                    // IMPORTANT: Do not modify the content at this stage (it might cause issues with tokenization)!
                    // Simply remove surrounding whitespace
                    parsedCollection.add(new InfoDocument(filteredID, StringUtils.stripToEmpty(segments[1])));
                }
            } catch (FileNotFoundException ex) {
                throw new IllegalArgumentException(String.format("The following file cannot be located: '%s'", filePath));
            } catch (IOException ex) {
                throw new IllegalArgumentException(String.format("Failed to read from the following file: '%s'. Cause: '%s'", filePath, ex.getCause().getMessage()));
            }
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(String.format("Failed to decode the following file path: '%s'", filePath));
        }
        return parsedCollection;
    }

    /**
     * Converts a standardized TREC formatted list of topics
     * into a list of Query objects
     * @param filePath Path to external queries to parse
     * @return List of Query objects
     */
    public static List<Query> parseQueries(String filePath) {
        List<Query> parsedQueries = new LinkedList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            String decodedFilePath = URLDecoder.decode(filePath, "UTF-8");
            try (InputStream inputStream = new FileInputStream(decodedFilePath)) {
                Document doc = factory.newDocumentBuilder().parse(inputStream);
                // ITERATE over the list of topics...
                NodeList rawTopics = doc.getDocumentElement().getChildNodes();
                int nodeNum = 1;
                for (int i = 0; i < rawTopics.getLength(); i++) {
                    Node topic = rawTopics.item(i);
                    if (topic.getNodeType() == Node.ELEMENT_NODE) {
                        Node topicId = topic.getAttributes().getNamedItem("id");

                        if (topicId == null || topicId.getNodeValue().isEmpty()) {
                            System.out.printf("Failed to locate 'id' attribute on topic (index: %d)", nodeNum);
                            nodeNum++;
                            continue;
                        }

                        // QUERY PROPERTIES!
                        String queryID = topicId.getNodeValue();
                        String queryContent = null;

                        NodeList topicMetadata = topic.getChildNodes();
                        for (int j = 0; j < topicMetadata.getLength(); j++) {
                            Node currentTopicMeta = topicMetadata.item(j);
                            if (currentTopicMeta.getNodeType() == Node.ELEMENT_NODE &&
                                    currentTopicMeta.getNodeName().equals("title")) {

                                // STRIP WHITESPACE
                                queryContent = StringUtils.stripToEmpty(currentTopicMeta.getTextContent());
                                break;
                            }
                        }

                        if (queryContent == null || queryContent.isEmpty()) {
                            System.out.printf("Failed to locate query content on topic (index: %d)", nodeNum);
                            nodeNum++;
                            continue;
                        }

                        parsedQueries.add(new Query(queryID, queryContent));
                        nodeNum++;
                    }

                }


            } catch (FileNotFoundException ex) {
                throw new IllegalArgumentException(String.format("The following file cannot be located: '%s'", filePath));
            } catch (ParserConfigurationException | SAXException | IOException ex) {
                throw new IllegalArgumentException(String.format("Failed to read from the following file: '%s'. Cause: '%s'", filePath, ex.getCause().getMessage()));
            }
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(String.format("Failed to decode the following file path: '%s'", filePath));
        }

        return parsedQueries;
    }

    /**
     * Parses a file for set of unique stop words!
     * @param filePath Path to external stop words to parse
     * @return Set of stop words
     */
    public static Set<String> parseStopWords(String filePath) {
        // TreeSet enables fast querying
        Set<String> stopWords = new TreeSet<>();
        try {
            String decodedFilePath = URLDecoder.decode(filePath, "UTF-8");
            try (BufferedReader fileReader = new BufferedReader(new FileReader(decodedFilePath))) {
                String currLine;
                while ((currLine = fileReader.readLine()) != null) {
                    stopWords.add(StringUtils.stripToEmpty(currLine));
                }
            } catch (FileNotFoundException ex) {
                throw new IllegalArgumentException(String.format("The following file cannot be located: '%s'", filePath));
            } catch (IOException ex) {
                throw new IllegalArgumentException(String.format("Failed to read from the following file: '%s'. Cause: '%s'", filePath, ex.getCause().getMessage()));
            }
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(String.format("Failed to decode the following file path: '%s'", filePath));
        }
        return stopWords;
    }


    /**
     * Generates a TREC results file derived from a list of ranked documents against a set of
     * executed queries
     * @param dirPath Directory to generate results file in..
     * @param rankedDocuments List of ranked documents
     */
    public static void generateResultsFile(String dirPath, List<RankedDocument> rankedDocuments) {
        final String resultsFileName = "Results.txt";
        try {
            String decodedDirPath = URLDecoder.decode(dirPath, "UTF-8");
            File directoryObj = new File(dirPath);
            if (!directoryObj.exists() || !directoryObj.isDirectory()) {
                throw new IllegalArgumentException(String.format("The following file cannot be located or isn't a directory: '%s'", decodedDirPath));
            }
            String resultsFilePath = String.format("%s\\%s", decodedDirPath, resultsFileName);
            try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(resultsFilePath))) {

                // ORDER DOCUMENTS BY TWO VECTORS:
                // DOC_ID && COSINE SCORE!
                rankedDocuments.sort((doc1, doc2) -> {
                    int queryIDComp = doc1.getQuery().getID().compareTo(doc2.getQuery().getID());
                    if (queryIDComp < 0) {
                        return -1;
                    } else if (queryIDComp == 0) {
                        if (doc1.getCosineScore() > doc2.getCosineScore()) {
                            return -1;
                        } else if (doc1.getCosineScore() == doc2.getCosineScore()) {
                            return 0;
                        }
                    }
                    return 1;
                });

                // Every run needs a unique identifier!
                String currentRunUUID = UUID.randomUUID().toString();
                String currentQueryID = rankedDocuments.get(0).getQuery().getID();
                int rankingCount = 0;
                for (int i = 0; i < rankedDocuments.size(); i++) {
                    RankedDocument currentDoc = rankedDocuments.get(i);
                    String latestQueryID = currentDoc.getQuery().getID();
                    if (!currentQueryID.equals(latestQueryID)) {
                        currentQueryID = latestQueryID;
                        rankingCount = 0;
                    }
                    rankingCount++;
                    // TREC format
                    // {QUERY_ID} Q0 {DOC_ID} {RANK} {COSINE_SCORE} {RUN_UUID}
                    // NOTE: the cosine score is up to 3 decimal points
                    // ASSUMPTION: space-delimited entries...
                    fileWriter.write(String.format("%s Q0 %s %d %,.3f %s%n",
                                                    latestQueryID,
                                                    currentDoc.getDocument().getID(),
                                                    rankingCount,
                                                    currentDoc.getCosineScore(),
                                                    currentRunUUID));
                }
            } catch (IOException ex) {
                throw new IllegalArgumentException(String.format("Failed to write to the following results file path: '%s'. Cause: '%s'", resultsFilePath, ex.getCause().getMessage()));
            }
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(String.format("Failed to decode the following file path: '%s'. Cause: '%s'", dirPath, ex.getCause().getMessage()));
        }
    }
}
