package io.inforet.microblog.tokenization;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.inforet.microblog.entities.RegexReplacement;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MicroblogTokenizer {

    /**
     * Lemmatization dictionary used to provide
     */
    private static final String LEMMATIZER_DICT = "./models/en-lemmatizer.dict";
    /**
     * Pre-trained model to provide parts-of-speech tagging
     */
    private static final String POS_TAGGER_MODEL_PATH = "./models/en-pos-maxent.bin";
    /**
     * Pre-trained model used to provide preliminary tokenization
     */
    private static final String TOKEN_MODEL_PATH = "./models/en-ud-ewt-tokens.bin";
    /**
     * Pre-trained model used to locate named entity persons (i.e., Britney Spears)
     */
    private static final String PERSON_NER_PATH = "./models/en-ner-person.bin";
    /**
     * Pre-trained model used to locate named entity organizations (i.e., Apple, Hewlett-Packard)
     */
    private static final String ORG_NER_PATH = "./models/en-ner-organization.bin";
    /**
     * Pre-trained model used to locate named entity locations (i.e., San Francisco)
     */
    private static final String LOCATION_NER_PATH = "./models/en-ner-location.bin";

    private final Tokenizer principalTokenizer;
    private final Lemmatizer dictionaryLemmatizer;
    private final POSTagger posTagger;
    private final TokenNameFinder personsNamedEntityFinder;
    private final TokenNameFinder orgNamedEntityFinder;
    private final TokenNameFinder locationNamedEntityFinder;

    private static final String REGEX_TOKEN_REPLACEMENTS = "replacements.json";

    public MicroblogTokenizer() {
        RuntimeException streamParseFailure = new IllegalArgumentException("Failed to parse model stream!");

        this.principalTokenizer = loadModel(TOKEN_MODEL_PATH, stream -> {
            try {
                TokenizerModel model = new TokenizerModel(stream);
                return new TokenizerME(model);
            } catch (IOException ex) {
                throw streamParseFailure;
            }
        });

        Function<InputStream, TokenNameFinder> nameFinderFunction = stream -> {
            try {
                TokenNameFinderModel model = new TokenNameFinderModel(stream);
                return new NameFinderME(model);
            } catch (IOException ex) {
                throw streamParseFailure;
            }
        };

        this.personsNamedEntityFinder = loadModel(PERSON_NER_PATH, nameFinderFunction);
        this.orgNamedEntityFinder = loadModel(ORG_NER_PATH, nameFinderFunction);
        this.locationNamedEntityFinder = loadModel(LOCATION_NER_PATH, nameFinderFunction);


        this.dictionaryLemmatizer = loadModel(LEMMATIZER_DICT, stream -> {
            try {
                return new DictionaryLemmatizer(stream);
            } catch (IOException ex) {
                throw streamParseFailure;
            }
        });

        this.posTagger = loadModel(POS_TAGGER_MODEL_PATH, stream -> {
            try {
                POSModel model = new POSModel(stream);
                return new POSTaggerME(model);
            } catch (IOException ex) {
                throw streamParseFailure;
            }
        });
    }

    private <T> T loadModel(String relModelPath, Function<InputStream, T> modelGen) {
        URL url = Thread.currentThread().getContextClassLoader().getResource(relModelPath);
        if (url == null) {
            throw new IllegalArgumentException(String.format("Failed to locate model path: '%s'", relModelPath));
        }
        try {
            String decodedURL = URLDecoder.decode(url.getPath(), "UTF-8");
            try (InputStream fileStream = new FileInputStream(decodedURL)) {
                return modelGen.apply(fileStream);
            } catch (FileNotFoundException ex) {
                throw new IllegalArgumentException(String.format("Failed to locate the following file: '%s'", decodedURL), ex);
            } catch (IOException ex) {
                throw new IllegalArgumentException(String.format("Failed to read the following file: '%s'", decodedURL), ex);
            }
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(String.format("Failed to decode the following path: '%s'. Cause: '%s'", url.getPath(), ex.getCause().getMessage()), ex);
        }
    }

    /**
     * Returns a list of tokens from the provided document content
     * @param document Represented as a document-unit (i.e, sentence, paragraph, chapter, etc.)
     * @return List of tokens
     */
    public String[] tokenizeDocument(String document) {
        List<String> strippedTokens = new ArrayList<>();

        // PHASE 1 : STEM URLS (Keep only the host details - this way, we can blacklist irrelevant hosts like
        // 'bit.ly')
        // We lose precision, but gain in recall!
        List<Pair<String, Boolean>> categorizedDocumentComponents = categorizeStemURIs(document);
        List<String> nonURIComponents = new ArrayList<>(categorizedDocumentComponents.size());
        for (Pair<String, Boolean> categorization: categorizedDocumentComponents) {
            if (Boolean.TRUE.equals(categorization.getRight())) {
                // THIS INDICATES A STEMMED URI - THERE'S NO NEED TO FURTHER PROCESS IT!
                strippedTokens.add(categorization.getLeft());
            } else {
                nonURIComponents.add(categorization.getLeft());
            }
        }

        String[] rawTokens = principalTokenizer.tokenize(String.join(StringUtils.SPACE, nonURIComponents.toArray(new String[0])));

        for (int i = 0; i < rawTokens.length; i++) {
            // PHASE 2 : CLEAN TOKENS
            // '#' prefix has a distinct meaning
            // '@' prefix has a distinct meaning
            String strippedToken = rawTokens[i].replaceAll("[^?#@0-9a-zA-Z]+", StringUtils.EMPTY);
            strippedToken = StringUtils.stripEnd(strippedToken, "#@");
            // '?' are used as a measure downstream to indicate trustworthy-ness of a tweet
            if (strippedToken.length() <= 1 &&
                    !strippedToken.equals("?")) {
                continue;
            }
            strippedTokens.add(strippedToken);
        }

        // PHASE 3 : Token expansion
        applyInnerWordExpansion(strippedTokens);
        applyHashtagExpansion(strippedTokens);

        // PHASE 4 : Apply word replacements
        String[] finalTokensList = applyReplacements(strippedTokens.toArray(new String[0]));

        // PHASE 5 : Named entity recognition
        return coalesceNameEntities(finalTokensList, 0.75, 3);
    }

    /**
     * Identifies a list of named entities from a list of tokens
     * @param tokens List of tokens
     * @return List of name entity spans, with positions derived from the provided list of tokens
     */
    public List<Span> findNamedEntities(String[] tokens) {
        TokenNameFinder[] nameFinders = { personsNamedEntityFinder, locationNamedEntityFinder, orgNamedEntityFinder };
        List<Span> foundEntities = new ArrayList<>();
        for(TokenNameFinder nameFinder: nameFinders) {
            foundEntities.addAll(Arrays.stream(nameFinder.find(tokens)).collect(Collectors.toList()));
        }
        return foundEntities;
    }

    /**
     * Performs a named entity recognition search against a list of provided tokens.
     * Each recognition is complimented with a probability - the probability threshold is intended
     * to filter out recognitions that are below this certainty boundary.
     * Each recognition that meets or exceeds the boundary modifies the token context (coalescing of tokens)
     * @param tokens List of reference tokens to seek named entities from
     * @param probabilityThreshold 0 <= value <= 1
     * @param entityLenThreshold Maximum named entity length
     * @return List of tokens
     */
    public String[] coalesceNameEntities(String[] tokens, double probabilityThreshold, int entityLenThreshold) {
        String[] coalescedTokens = Arrays.stream(tokens).toArray(String[]::new);
        List<Span> foundEntities = findNamedEntities(coalescedTokens);
        // SORT the named entities by their span length && probabilities
        // Shorter the span, higher the priority (Assumption: rarely do large aggregation of words form a meaningful named entity)
        // Larger the probability, higher the priority
        foundEntities.sort((e1,e2) -> {
            int firstSpanLen = e1.getEnd() - e1.getStart();
            int secondSpanLen = e2.getEnd() - e2.getStart();
            if (firstSpanLen < secondSpanLen) {
                return -1;
            } else if (firstSpanLen == secondSpanLen) {
                if (e1.getProb() > e2.getProb()) {
                    return -1;
                } else if (e1.getProb() == e2.getProb()) {
                    return 0;
                }
            }
            return 1;
        });

        for (Span foundEntity: foundEntities) {

            if (foundEntity.getProb() < probabilityThreshold ||
                    (foundEntity.getEnd() - foundEntity.getStart()) > entityLenThreshold) {
                continue;
            }

            List<String> coalescedTokenComponents = new ArrayList<>();
            for (int i = foundEntity.getStart(); i < foundEntity.getEnd(); i++) {
                // INDICATES THAT THE POSITION HAS BEEN OCCUPIED BY ANOTHER NAMED ENTITY
                if (coalescedTokens[i] == null) {
                    coalescedTokenComponents = null;
                    break;
                }
                coalescedTokenComponents.add(coalescedTokens[i]);
            }

            if (coalescedTokenComponents != null) {
                coalescedTokens[foundEntity.getStart()] = String.join(StringUtils.SPACE, coalescedTokenComponents);
                for (int i = foundEntity.getStart() + 1; i < foundEntity.getEnd(); i++) {
                    coalescedTokens[i] = null;
                }
            }

        }

        return Arrays.stream(coalescedTokens)
                        .filter(Objects::nonNull).toArray(String[]::new);
    }

    /**
     * Categorizes tokens are either being URIs or not. Furthermore, the tokens that
     * are URIs are stemmed to their host value.
     * i.e, https://bit.ly/1294ndkj   =>    www.bit.ly
     * @param document Document to parse
     * @return List of categorized tokens on whether they're URIs or not
     */
    private List<Pair<String, Boolean>> categorizeStemURIs(String document) {
        String[] documentComponents = document.split(StringUtils.SPACE);
        List<Pair<String, Boolean>> categorizedComponents = new ArrayList<>();
        for(int i = 0; i < documentComponents.length; i++) {
            try {
                String placeholder = new URI(documentComponents[i]).getHost();
                if (placeholder != null && !placeholder.isEmpty()) {
                    categorizedComponents.add(new ImmutablePair<>(String.format("%s%s","www.",StringUtils.stripStart(placeholder,"w.")), true));
                } else {
                    categorizedComponents.add(new ImmutablePair<>(documentComponents[i], false));
                }
            }catch (URISyntaxException ex) {}
        }
        return categorizedComponents;
    }

    /**
     * Expand hashtag content into its own token, while preserving the full hashtag in the list of tokens
     * @param tokens List of tokens
     */
    private void applyHashtagExpansion(List<String> tokens) {
        String[] tmp = tokens.toArray(new String[0]);
        for (String token : tmp) {
            // REGEX isn't complete, but sufficient for most use cases
            Pattern hashtagPattern = Pattern.compile("^[#]+[a-zA-Z0-9_]+");
            Matcher matcher = hashtagPattern.matcher(token);
            if (matcher.matches()) {
                tokens.add(StringUtils.stripStart(token, "#"));
            }
        }
    }

    /**
     * Some words may be compounded together without delineation (i.e, BBCNewsNetwork).
     * Delineate compounded words into individual tokens (i.e, BBCNewsNetwork -> ['BBC', 'News', 'Network'])
     * @param tokens List of tokens to parse
     */
    private void applyInnerWordExpansion(List<String> tokens) {
        String[] tmp = tokens.toArray(new String[0]);
        for (String token : tmp) {
            Character[] tokenCharacters = ArrayUtils.toObject(token.toCharArray());
            Pattern innerWordDelimiter = Pattern.compile("[A-Z]{1}[a-z]+");
            Matcher matcher = innerWordDelimiter.matcher(token);
            // We don't care about full matches - we care about multiple occurrence matches within a string
            MatchResult[] matchResults = matcher.results().toArray(MatchResult[]::new);
            if ( matchResults.length > 1 ||
                    ( matchResults.length == 1 && ( matchResults[0].end() - matchResults[0].start() ) < token.length() ) ) {
                for (MatchResult matchResult : matchResults) {
                    tokens.add(token.substring(matchResult.start(), matchResult.end()));
                    for (int i = matchResult.start(); i < matchResult.end(); i++) {
                        tokenCharacters[i] = null;
                    }
                }

                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < tokenCharacters.length; i++) {
                    Character remainingChar = tokenCharacters[i];
                    if (remainingChar != null) {
                        builder.append(remainingChar);
                    }
                    if (remainingChar == null || i == (tokenCharacters.length - 1)) {
                        if (builder.length() > 1) {
                            tokens.add(builder.toString());
                        }
                        builder.setLength(0);
                    }
                }
            }
        }
    }

    /**
     * Executes a simple token replacement procedure against a set of tokens,
     * to account for common abbreviations, short-hand notations, etc.
     * This is not a replacement for spell correction, but rather a precursor.
     * (i.e, "u" => "you", "plzz" => "please", "wym" => "what do you mean" )
     * An alternative measure from normalizations using equivalence classes (less precision),
     * but maintains a slight boost in recall.
     * @param tokens List of tokens to process
     * @return
     */
    private String[] applyReplacements(String[] tokens) {
        URL url = Thread.currentThread().getContextClassLoader().getResource(REGEX_TOKEN_REPLACEMENTS);
        if (url == null) {
            return tokens;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<RegexReplacement> regexReplacementList = mapper.readValue(url, new TypeReference<List<RegexReplacement>>() {});
            for (int i = 0; i < tokens.length; i++) {
                for(RegexReplacement regexReplacement: regexReplacementList) {
                    Pattern pattern = Pattern.compile(regexReplacement.getRegex());
                    Matcher matcher = pattern.matcher(tokens[i]);
                    // IT MUST MATCH THE FULL TEXT CONTENT TO BE REPLACED.
                    // MULTIPLE OCCURRENCES ARE NOT CONSIDERED...
                    if (matcher.matches()) {
                        tokens[i] = regexReplacement.getReplacement();
                        break;
                    }
                }
            }
            return tokens;
        } catch (JsonMappingException | JsonParseException ex) {
            throw new IllegalArgumentException(String.format("Failed to parse the following URL: '%s'. Cause: '%s'", url.getPath(), ex.getCause().getMessage()));
        } catch (IOException ex) {
            throw new IllegalArgumentException(String.format("Failed to read the following URL: '%s'. Cause: '%s'", url.getPath(), ex.getCause().getMessage()));
        }
    }

    /**
     * Linguistically morph a term into various forms & permutations
     * @param term Term to pre-process
     * @return A list of term variations
     */
    public Collection<String> normalize(String term) {
        Set<String> variations = new LinkedHashSet<>();
        // Original...
        Set<String> rootTerms = Arrays.stream(term.split(StringUtils.SPACE)).collect(Collectors.toCollection(LinkedHashSet::new));
        rootTerms.add(term);

        for (String rootTerm : rootTerms) {
            variations.add(StringUtils.capitalize(rootTerm.toLowerCase(Locale.ROOT)));
            variations.add(rootTerm.toLowerCase(Locale.ROOT));
            variations.add(rootTerm.toUpperCase(Locale.ROOT));
        }
        return variations;
    }

}
