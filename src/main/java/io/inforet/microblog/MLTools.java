package io.inforet.microblog;

import io.inforet.microblog.entities.InfoDocument;
import io.inforet.microblog.entities.InfoDocumentIterator;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.sentenceiterator.LineSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.common.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Intended to enrich core application is machine learning facilities (i.e., training, loading, configuring models)
 */
public class MLTools {

    private MLTools() {}

    /**
     * Build a Word2Vector model using a list of InfoDocument objects
     * @param dataset InfoDocument objects (TREC dataset content)
     * @return Word2Vector model
     */
    public static Word2Vec buildWord2VecModel(List<InfoDocument> dataset) {
        SentenceIterator iterator = new InfoDocumentIterator(dataset);
        return buildWord2VecModel(iterator);
    }

    /**
     * Build a Word2Vector model from a local resource path
     * @param datasetResourcePath Local resource path to a dataset composed of newline delimited sentences to train model against
     * @return Word2Vector model
     */
    public static Word2Vec buildWord2VecModel(String datasetResourcePath) {
        try {
            File dataset = new ClassPathResource(datasetResourcePath).getFile();
            SentenceIterator iterator = new LineSentenceIterator(dataset);
            return buildWord2VecModel(iterator);
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                    String.format("Failed to load Word2Vec dataset! Resource: '%s'. Reason: '%s'", datasetResourcePath, ex.getMessage()));
        }
    }

    /**
     * Builds a Word2Vector model.
     * Documentation: https://deeplearning4j.konduit.ai/deeplearning4j/reference/word2vec-glove-doc2vec
     * @param iterator Tells the neural net what batch of the dataset it's training on
     * @return Word2Vector model
     */
    private static Word2Vec buildWord2VecModel(SentenceIterator iterator) {
        TokenizerFactory t = new DefaultTokenizerFactory();
        t.setTokenPreProcessor(new CommonPreprocessor());
        // DOCUMENTATION : https://deeplearning4j.konduit.ai/deeplearning4j/reference/word2vec-glove-doc2vec#training-the-model
        Word2Vec w2vModel = new Word2Vec.Builder()
                .minWordFrequency(7)
                .layerSize(50)
                .seed(42)
                .windowSize(5)
                .iterate(iterator)
                .tokenizerFactory(t)
                .build();
        w2vModel.fit();
        return w2vModel;
    }

}
