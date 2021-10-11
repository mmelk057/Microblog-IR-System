package io.inforet.microblog.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RegexReplacement {
    private String regex;
    private String replacement;

    @JsonCreator
    public RegexReplacement(@JsonProperty(required = true, value = "regex")
                            String regex,
                            @JsonProperty(required = true, value = "replacement")
                            String replacement) {
        this.regex = regex;
        this.replacement = replacement;
    }

    public String getRegex() {
        return regex;
    }

    public String getReplacement() {
        return replacement;
    }
}
