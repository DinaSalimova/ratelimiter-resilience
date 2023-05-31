package com.example.ratelimiterresilience;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
public class ResilientAppController {
    private final ExternalAPICaller externalAPICaller;

    @Autowired
    public ResilientAppController(ExternalAPICaller externalAPICaller) {
        this.externalAPICaller = externalAPICaller;
    }

    @GetMapping("/rate-limiter")
    @RateLimiter(name = "rateLimiterApi")
    public String rateLimitApi() {
        return externalAPICaller.callApi();
    }

    @GetMapping("/retry")
    @Retry(name = "retryApi", fallbackMethod = "fallbackAfterRetry")
    public String retryApi() {
        return externalAPICaller.callApi();
    }

    public String fallbackAfterRetry(Exception ex) {
        return "all retries have exhausted";
    }

    @GetMapping("/time-limiter")
    @TimeLimiter(name = "timeLimiterApi")
    public CompletableFuture<String> timeLimiterApi() {
        return CompletableFuture.supplyAsync(externalAPICaller::callApiWithDelay);
    }
}