package io.inforet.microblog;

import io.inforet.microblog.entities.InfoDocument;
import io.inforet.microblog.entities.Query;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class TRECTools {

    /**
     * Converts a provided TREC formatted list of documents
     * into a list of Document objects
     * @param filePath Path to external documents to parse
     * @return List of Document objects
     */
    public static List<InfoDocument> parseCollection(String filePath) {
        List<InfoDocument> parsedCollection = new LinkedList<>();
        BufferedReader fileReader = null;
        try {
            fileReader = new BufferedReader(new FileReader(filePath));
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
            System.out.printf("The following file cannot be located: '%s'", filePath);
        } catch (IOException ex) {
            System.out.printf("Failed to read from the following file: '%s'. Cause: '%s'", filePath, ex.getCause().getMessage());
        } finally {
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException ex) {
                    System.out.printf("Failed in an attempt to close the following file: '%s'. Cause: '%s'", filePath, ex.getCause().getMessage());
                }
            }
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
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(filePath);
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
            System.out.printf("The following file cannot be located: '%s'", filePath);
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            System.out.printf("Failed to parse the following file: '%s'. Cause: '%s'", filePath, ex.getCause().getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    System.out.printf("Failed in an attempt to close the following file: '%s'. Cause: '%s'", filePath, ex.getCause().getMessage());
                }

            }
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
        BufferedReader fileReader = null;
        try {
            fileReader = new BufferedReader(new FileReader(filePath));
            String currLine;
            while((currLine = fileReader.readLine()) != null) {
                stopWords.add(StringUtils.stripToEmpty(currLine));
            }
        } catch (FileNotFoundException ex) {
            System.out.printf("The following file cannot be located: '%s'", filePath);
        } catch (IOException ex) {
            System.out.printf("Failed to read from the following file: '%s'. Cause: '%s'", filePath, ex.getCause().getMessage());
        } finally {
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException ex) {
                    System.out.printf("Failed in an attempt to close the following file: '%s'. Cause: '%s'", filePath, ex.getCause().getMessage());
                }
            }
        }
        return stopWords;
    }

}