package io.getint.recruitment_task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JiraSynchronizer {

    private static final String JIRA_URL = "your jira instance";
    private static final String SOURCE_PROJECT_KEY = "your source project key";
    private static final String TARGET_PROJECT_KEY = "your target project key";
    private static final String USERNAME = "your useername";
    private static final String PASSWORD = "your api key";
    private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());

    private static final Map<String, String> STATUS_NAME_TO_ID_MAP = new HashMap<>();

    static {
        STATUS_NAME_TO_ID_MAP.put("To Do", "11");
        STATUS_NAME_TO_ID_MAP.put("In Progress", "21");
        STATUS_NAME_TO_ID_MAP.put("Done", "31");
    }

    public static void main(String[] args) {
        moveTasksToOtherProject();
    }

    public static void moveTasksToOtherProject() {
        try {
            JiraResponse jiraResponse = fetchIssues();
            if (jiraResponse != null) {
                moveIssuesToBoard(jiraResponse.getIssues());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static JiraResponse fetchIssues() throws IOException {
        String jql = "project=" + SOURCE_PROJECT_KEY + " ORDER BY created DESC";
        String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);
        String uri = JIRA_URL + "/rest/api/2/search?jql=" + encodedJql + "&maxResults=5";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(uri);
            setHeaders(request);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    String jsonResponse = EntityUtils.toString(response.getEntity());
                    ObjectMapper mapper = new ObjectMapper();
                    JiraResponse jiraResponse = mapper.readValue(jsonResponse, JiraResponse.class);

                    for (Issue issue : jiraResponse.getIssues()) {
                        List<Comment> comments = fetchComments(issue.getId());
                        issue.setComment(comments);
                    }
                    return jiraResponse;
                } else {
                    System.out.println("Failed to fetch issues. HTTP error code: " + response.getStatusLine().getStatusCode());
                    return null;
                }
            }
        }
    }

    public static List<Comment> fetchComments(String issueId) throws IOException {
        String uri = JIRA_URL + "/rest/api/2/issue/" + issueId + "/comment";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(uri);
            setHeaders(request);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    String jsonResponse = EntityUtils.toString(response.getEntity());
                    ObjectMapper mapper = new ObjectMapper();
                    CommentResponse commentResponse = mapper.readValue(jsonResponse, CommentResponse.class);
                    return commentResponse.getComments();
                } else {
                    System.out.println("Failed to fetch comments for issue " + issueId + ". HTTP error code: " + response.getStatusLine().getStatusCode());
                    return null;
                }
            }
        }
    }

    public static void moveIssuesToBoard(List<Issue> issues) throws IOException {
        for (Issue issue : issues) {
            moveIssueToBoard(issue);
        }
    }

    public static void moveIssueToBoard(Issue issue) throws IOException {
        String uri = JIRA_URL + "/rest/api/2/issue/";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPostRequest = new HttpPost(uri);
            setHeaders(httpPostRequest);

            StringEntity stringEntity = getJsonPayload(issue);
            httpPostRequest.setEntity(stringEntity);

            try (CloseableHttpResponse response = httpClient.execute(httpPostRequest)) {
                HttpEntity httpEntity = response.getEntity();
                String entityString = EntityUtils.toString(httpEntity);
                JSONObject jsonObject = new JSONObject(entityString);
                String id = jsonObject.getString("id");

                for (Comment comment : issue.getComment()) {
                    addCommentToIssue(comment, id);
                }

                if (response.getStatusLine().getStatusCode() == 201) {
                    System.out.println("Issue " + issue.getId() + " moved successfully.");
                    updateIssueStatus(id, issue.getFields().getStatus().getName());
                } else {
                    System.out.println("Failed to move issue " + issue.getId() + ". HTTP error code: " + response.getStatusLine().getStatusCode());
                }
            }
        }
    }

    public static void updateIssueStatus(String issueId, String statusName) throws IOException {
        String statusId = getStatusIdByName(statusName);
        if (statusId == null) {
            System.out.println("Failed to update status: Status name " + statusName + " not found.");
            return;
        }

        String uri = JIRA_URL + "/rest/api/2/issue/" + issueId + "/transitions";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPostRequest = new HttpPost(uri);
            setHeaders(httpPostRequest);

            String jsonPayload = String.format(
                    "{" +
                            "\"transition\": {" +
                            "\"id\": \"%s\"" +
                            "}" +
                            "}",
                    statusId
            );

            StringEntity stringEntity = new StringEntity(jsonPayload);
            stringEntity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
            httpPostRequest.setEntity(stringEntity);

            try (CloseableHttpResponse response = httpClient.execute(httpPostRequest)) {
                if (response.getStatusLine().getStatusCode() == 204) {
                    System.out.println("Status of issue " + issueId + " updated to " + statusName + " successfully.");
                } else {
                    System.out.println("Failed to update status of issue " + issueId + ". HTTP error code: " + response.getStatusLine().getStatusCode());
                }
            }
        }
    }

    public static String getStatusIdByName(String statusName) {
        return STATUS_NAME_TO_ID_MAP.get(statusName);
    }

    public static void addCommentToIssue(Comment comment, String id) {
        try {
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost(JIRA_URL + "/rest/api/2/issue/" + id + "/comment");
                String json = ("{" +
                        "     \"body\": \"" + comment.getBody() + "\"" +
                        "}");
                setHeaders(httpPost);

                StringEntity entity = new StringEntity(json);
                entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
                httpPost.setEntity(entity);

                httpclient.execute(httpPost);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private static StringEntity getJsonPayload(Issue issue) throws UnsupportedEncodingException {
        String jsonPayload = String.format(
                "{" +
                        "\"fields\": {" +
                        "\"project\": {\"key\": \"%s\"}," +
                        "\"summary\": \"%s\"," +
                        "\"description\": \"%s\"," +
                        "\"issuetype\": {\"name\": \"%s\"}" +
                        "}" +
                        "}",
                TARGET_PROJECT_KEY,
                issue.getFields().getSummary(),
                issue.getFields().getDescription(),
                issue.getFields().getIssuetype().getName()
        );

        StringEntity stringEntity = new StringEntity(jsonPayload);
        stringEntity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        return stringEntity;
    }

    private static void setHeaders(HttpRequestBase requestBase) {
        requestBase.setHeader("Authorization", AUTH);
        requestBase.setHeader("Content-Type", "application/json");
    }
}
