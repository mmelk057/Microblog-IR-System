package io.inforet.microblog.entities;

public class RankedDocument {
    private Query query;
    private InfoDocument document;
    private double cosineScore;

    public RankedDocument(Query query, InfoDocument document, double cosineScore) {
        this.query = query;
        this.document = document;
        this.cosineScore = cosineScore;
    }

    public Query getQuery() {
        return query;
    }

    public InfoDocument getDocument() {
        return document;
    }

    public double getCosineScore() {
        return cosineScore;
    }
}
