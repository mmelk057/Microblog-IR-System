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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class TRECTools {

    /**
     * Converts a provided TREC formatted list of documents
     * into a list of Document objects
     *
     * @param fileStream Input stream
     * @return List of Document objects
     */
    public static List<InfoDocument> parseCollection(InputStream fileStream) {
        List<InfoDocument> parsedCollection = new LinkedList<>();
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(fileStream))) {
            String currLine;
            while ((currLine = fileReader.readLine()) != null) {
                // TREC document collections specify TWO segments, tab delimited!
                String[] segments = currLine.split("\t");
                assert segments.length == 2;
                // Numeric ID!
                String filteredID = StringUtils.stripToEmpty(segments[0]).replaceAll("[^0-9]", "");
                // IMPORTANT: Do not modify the content at this stage (it might cause issues with tokenization)!
                // Simply remove surrounding whitespace
                String content = StringUtils.stripToEmpty(segments[1]);
                // Filter out retweets & mentions...
                // https://trec.nist.gov/pubs/trec20/papers/MICROBLOG.OVERVIEW.pdf
                List<String> simpleDelim = Arrays.asList(content.split(StringUtils.SPACE));
                if (!simpleDelim.contains("RT") &&
                        !simpleDelim.contains("MT")) {
                    parsedCollection.add(new InfoDocument(filteredID, content));
                }
            }
        } catch (FileNotFoundException ex) {
            throw new IllegalArgumentException(String.format("Unable to parse collection - file not found. Cause: '%s'", ex.getCause().getMessage()));
        } catch (IOException ex) {
            throw new IllegalArgumentException(String.format("Unable to parse collection - cannot read file. Cause: '%s'", ex.getCause().getMessage()));
        }
        return parsedCollection;
    }

    /**
     * Converts a standardized TREC formatted list of topics
     * into a list of Query objects
     *
     * @param fileStream Input stream
     * @return List of Query objects
     */
    public static List<Query> parseQueries(InputStream fileStream) {
        List<Query> parsedQueries = new LinkedList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            Document doc = factory.newDocumentBuilder().parse(fileStream);
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
            throw new IllegalArgumentException(String.format("Unable to parse queries - file not found. Cause: '%s'", ex.getCause().getMessage()));
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new IllegalArgumentException(String.format("Unable to parse queries - file cannot be read. Cause: '%s'", ex.getCause().getMessage()));
        }

        return parsedQueries;
    }

    /**
     * Parses a file for set of unique stop words!
     *
     * @param fileStream Input stream
     * @return Set of stop words
     */
    public static Set<String> parseStopWords(InputStream fileStream) {
        // TreeSet enables fast querying
        Set<String> stopWords = new TreeSet<>();
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(fileStream))) {
            String currLine;
            while ((currLine = fileReader.readLine()) != null) {
                stopWords.add(StringUtils.stripToEmpty(currLine));
            }
        } catch (FileNotFoundException ex) {
            throw new IllegalArgumentException(String.format("Unable to parse stop words - file not found. Cause: '%s'", ex.getCause().getMessage()));
        } catch (IOException ex) {
            throw new IllegalArgumentException(String.format("Unable to parse stop words - failed to read file. Cause: '%s'", ex.getCause().getMessage()));
        }

        return stopWords;
    }
}
