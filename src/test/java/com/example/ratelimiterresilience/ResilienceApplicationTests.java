package com.example.ratelimiterresilience;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResilienceApplicationTests {

    @RegisterExtension
    static WireMockExtension wireMockServer = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig()
                    .port(9090))
            .build();

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testRatelimiter() {
        wireMockServer.stubFor(WireMock.get("/api/external")
                .willReturn(ok()));
        Map<Integer, Integer> responseStatusCount = new ConcurrentHashMap<>();

        IntStream.rangeClosed(1, 50)
                .parallel()
                .forEach(i -> {
                    ResponseEntity<String> response = restTemplate.getForEntity("/api/rate-limiter", String.class);
                    int statusCode = response.getStatusCodeValue();
                    responseStatusCount.put(statusCode, responseStatusCount.getOrDefault(statusCode, 0) + 1);
                });

        assertEquals(2, responseStatusCount.keySet().size());
        assertTrue(responseStatusCount.containsKey(TOO_MANY_REQUESTS.value()));
        assertTrue(responseStatusCount.containsKey(OK.value()));
        wireMockServer.verify(5, getRequestedFor(urlEqualTo("/api/external")));
    }

    @Test
    public void testRetry() {
        wireMockServer.stubFor(WireMock.get("/api/external")
                .willReturn(ok()));
        ResponseEntity<String> response1 = restTemplate.getForEntity("/api/retry", String.class);
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/api/external")));

        wireMockServer.resetRequests();

        wireMockServer.stubFor(WireMock.get("/api/external")
                .willReturn(serverError()));
        ResponseEntity<String> response2 = restTemplate.getForEntity("/api/retry", String.class);
        assertEquals(response2.getBody(), "all retries have exhausted");
        wireMockServer.verify(3, getRequestedFor(urlEqualTo("/api/external"))); // as we have max-attempts=3
    }

    @Test
    public void testTimeLimiter() {
        wireMockServer.stubFor(WireMock.get("/api/external").willReturn(ok()));
        ResponseEntity<String> response = restTemplate.getForEntity("/api/time-limiter", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/api/external")));
    }
}
