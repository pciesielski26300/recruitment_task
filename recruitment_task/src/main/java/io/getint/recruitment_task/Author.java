package io.getint.recruitment_task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Author {


    private String name;
    private String displayName;
    private String emailAddress;
}
