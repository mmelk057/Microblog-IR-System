package io.inforet.microblog.entities;

public class InfoDocument {
    private String ID;
    private String Document;

    public InfoDocument(String ID, String Document) {
        this.ID = ID;
        this.Document = Document;
    }

    public String getID() {
        return ID;
    }

    public String getDocument() {
        return Document;
    }
}
