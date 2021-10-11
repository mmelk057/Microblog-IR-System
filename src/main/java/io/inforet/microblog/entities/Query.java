package io.inforet.microblog.entities;

/**
 * Every Query object has an associated:
 * - ID
 * - Query (document)
 */
public class Query {
    private String ID;
    private String Query;

    public Query(String ID, String Query) {
        this.ID = ID;
        this.Query = Query;
    }

    public String getID() {
        return ID;
    }

    public String getQuery() {
        return Query;
    }
}
