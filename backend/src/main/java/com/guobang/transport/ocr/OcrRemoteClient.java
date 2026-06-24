package com.guobang.transport.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guobang.transport.common.BusinessException;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OcrRemoteClient {
    @Value("${ocr.paddle.api-url}")
    private String paddleApiUrl;

    @Value("${ocr.paddle.api-token}")
    private String paddleApiToken;

    @Value("${ocr.paddle.model}")
    private String paddleModel;

    @Value("${ocr.paddle.timeout-sec}")
    private int paddleTimeoutSec;

    @Value("${ocr.paddle.poll-interval-sec}")
    private int paddlePollIntervalSec;

    @Value("${ocr.baidu.api-key}")
    private String baiduApiKey;

    @Value("${ocr.baidu.secret-key}")
    private String baiduSecretKey;

    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public String callPaddleOcr(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException("image bytes empty", HttpStatus.BAD_REQUEST);
        }
        try {
            String boundary = "----OcrBoundary" + System.currentTimeMillis();
            String crlf = "\r\n";
            StringBuilder sb = new StringBuilder();
            sb.append("--").append(boundary).append(crlf);
            sb.append("Content-Disposition: form-data; name=\"model\"").append(crlf).append(crlf);
            sb.append(paddleModel).append(crlf);
            sb.append("--").append(boundary).append(crlf);
            sb.append("Content-Disposition: form-data; name=\"optionalPayload\"").append(crlf).append(crlf);
            sb.append("{\"useDocOrientationClassify\":false,\"useDocUnwarping\":false,\"useChartRecognition\":false}").append(crlf);
            sb.append("--").append(boundary).append(crlf);
            sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"image.jpg\"").append(crlf);
            sb.append("Content-Type: image/jpeg").append(crlf).append(crlf);
            byte[] headerBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            byte[] footerBytes = (crlf + "--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8);
            byte[] body = new byte[headerBytes.length + imageBytes.length + footerBytes.length];
            System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
            System.arraycopy(imageBytes, 0, body, headerBytes.length, imageBytes.length);
            System.arraycopy(footerBytes, 0, body, headerBytes.length + imageBytes.length, footerBytes.length);

            HttpRequest submitReq = HttpRequest.newBuilder()
                    .uri(URI.create(paddleApiUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "bearer " + paddleApiToken)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> submitResp = httpClient.send(submitReq, HttpResponse.BodyHandlers.ofString());
            if (submitResp.statusCode() != 200) {
                throw new Exception("Submit HTTP " + submitResp.statusCode() + ": "
                        + submitResp.body().substring(0, Math.min(300, submitResp.body().length())));
            }
            String jobId = mapper.readTree(submitResp.body()).path("data").path("jobId").asText("");
            if (jobId.isEmpty()) {
                throw new Exception("No jobId in response: " + submitResp.body());
            }

            long deadline = System.currentTimeMillis() + (long) paddleTimeoutSec * 1000;
            String resultJsonUrl = "";
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(paddlePollIntervalSec * 1000L);
                HttpRequest pollReq = HttpRequest.newBuilder()
                        .uri(URI.create(paddleApiUrl + "/" + jobId))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "bearer " + paddleApiToken)
                        .GET()
                        .build();
                HttpResponse<String> pollResp = httpClient.send(pollReq, HttpResponse.BodyHandlers.ofString());
                if (pollResp.statusCode() != 200) {
                    throw new Exception("Poll HTTP " + pollResp.statusCode());
                }
                JsonNode data = mapper.readTree(pollResp.body()).path("data");
                String state = data.path("state").asText("");
                if ("done".equals(state)) {
                    resultJsonUrl = data.path("resultUrl").path("jsonUrl").asText("");
                    break;
                }
                if ("failed".equals(state)) {
                    throw new Exception("AIStudio job failed: " + data.path("errorMsg").asText("unknown"));
                }
                if (!"pending".equals(state) && !"running".equals(state)) {
                    throw new Exception("AIStudio unknown state: " + state);
                }
            }
            if (resultJsonUrl.isEmpty()) {
                throw new TimeoutException("AIStudio job timeout after " + paddleTimeoutSec + "s");
            }

            HttpRequest resultReq = HttpRequest.newBuilder()
                    .uri(URI.create(resultJsonUrl))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<String> resultResp = httpClient.send(resultReq, HttpResponse.BodyHandlers.ofString());
            if (resultResp.statusCode() != 200) {
                throw new Exception("Result HTTP " + resultResp.statusCode());
            }
            return parseAiStudioResult(resultResp.body());
        } catch (Exception e) {
            throw new BusinessException("PaddleOCR failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    public String callBaiduOcr(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException("image bytes empty", HttpStatus.BAD_REQUEST);
        }
        try {
            String token = getBaiduToken();
            String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
            String body = "image=" + URLEncoder.encode(imageBase64, StandardCharsets.UTF_8)
                    + "&language_type=CHN_ENG&detect_direction=false&paragraph=false&probability=false";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic?access_token=" + token))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new Exception("HTTP " + resp.statusCode());
            }
            return parseBaiduResponse(resp.body());
        } catch (Exception e) {
            throw new BusinessException("Baidu OCR failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private String parseAiStudioResult(String jsonlText) {
        List<String> lines = new ArrayList<>();
        for (String rawLine : jsonlText.split("\n")) {
            rawLine = rawLine.trim();
            if (rawLine.isEmpty()) {
                continue;
            }
            try {
                JsonNode result = mapper.readTree(rawLine).path("result");
                for (JsonNode res : result.path("layoutParsingResults")) {
                    String text = res.path("markdown").path("text").asText("");
                    for (String line : text.split("\n")) {
                        line = line.strip();
                        if (!line.isEmpty()) {
                            lines.add(line);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return String.join("\n", lines);
    }

    private String parseBaiduResponse(String responseBody) {
        try {
            JsonNode words = mapper.readTree(responseBody).get("words_result");
            if (words == null || !words.isArray()) {
                return responseBody;
            }
            List<String> lines = new ArrayList<>();
            for (JsonNode item : words) {
                String line = item.path("words").asText("");
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return String.join("\n", lines);
        } catch (Exception e) {
            return responseBody;
        }
    }

    private String getBaiduToken() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials"
                            + "&client_id=" + baiduApiKey + "&client_secret=" + baiduSecretKey))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new Exception("HTTP " + resp.statusCode());
            }
            String accessToken = mapper.readTree(resp.body()).path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new Exception("No access_token");
            }
            return accessToken;
        } catch (Exception e) {
            throw new BusinessException("Baidu token failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }
}
