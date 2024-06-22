package io.getint.recruitment_task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class Fields {

    private String summary;
    private String description;
    private IssueType issuetype;
    private Status status;
}
