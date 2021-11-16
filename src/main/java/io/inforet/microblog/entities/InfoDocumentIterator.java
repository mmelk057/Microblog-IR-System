package io.inforet.microblog.entities;

import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;

import java.util.List;

/**
 * Iterate over a list of InfoDocument objects
 */
public class InfoDocumentIterator implements SentenceIterator {
    private List<InfoDocument> documentList;
    private int documentIndex;
    private SentencePreProcessor preProcessor;

    public InfoDocumentIterator(List<InfoDocument> documentList) {
        this.documentList = documentList;
        this.documentIndex = 0;
    }

    @Override
    public String nextSentence() {
        InfoDocument selected = documentList.get(documentIndex);
        documentIndex++;
        return selected.getDocument();
    }

    @Override
    public boolean hasNext() {
        return this.documentList != null &&
                !this.documentList.isEmpty() &&
                this.documentIndex < this.documentList.size();
    }

    @Override
    public void reset() {
        this.documentIndex = 0;
    }

    @Override
    public void finish() {
        // NO NEED TO DO ANYTHING HERE! THIS METHOD IS RESERVED FOR STREAM CLOSING BEHAVIOR
    }

    @Override
    public SentencePreProcessor getPreProcessor() {
        return this.preProcessor;
    }

    @Override
    public void setPreProcessor(SentencePreProcessor preProcessor) {
        this.preProcessor = preProcessor;
    }
}
