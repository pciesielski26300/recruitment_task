package io.getint.recruitment_task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Comment {

    public String id;
    private String body;
    private Author author;
    private String created;
}