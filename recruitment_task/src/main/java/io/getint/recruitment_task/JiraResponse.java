package io.getint.recruitment_task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;


@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraResponse {

    private List<Issue> issues;
}
