package io.getint.recruitment_task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Issue {

    private String id;
    private String key;
    private Fields fields;
    private List<Comment> comment;

}
