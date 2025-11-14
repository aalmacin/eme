# Agent #1: Smart Error Recovery Agent

[← Back to Main Document](../../AGENTIC_AI_INTEGRATION.md) | [← Previous: Current State](02-current-state.md) | [Next: Quality Assurance Agent →](04-agent-quality-assurance.md)

---

## Executive Summary

The **Smart Error Recovery Agent** is an autonomous troubleshooting system that detects, diagnoses, and recovers from API failures without human intervention. It transforms EME from a system that requires manual debugging to a self-healing platform.

**Priority:** HIGH (Phase 1 - implement first)
**Estimated Effort:** 40 hours
**Expected ROI:** 95% reduction in manual error handling within 2 weeks

---

## Table of Contents

1. [Problem Statement](#problem-statement)
2. [Agent Capabilities](#agent-capabilities)
3. [Technical Architecture](#technical-architecture)
4. [Implementation Specification](#implementation-specification)
5. [Database Schema Changes](#database-schema-changes)
6. [Integration Pattern](#integration-pattern)
7. [Testing Strategy](#testing-strategy)
8. [Metrics & Monitoring](#metrics--monitoring)
9. [Rollout Plan](#rollout-plan)

---

## Problem Statement

### Current Pain Points

**Scenario 1: Rate Limit Errors**
```
User submits batch of 20 words
↓
OpenAI API rate limit hit after 5 words
↓
Session fails with "429 Too Many Requests"
↓
Developer must:
  1. Manually inspect session logs
  2. Identify which words succeeded/failed
  3. Restart session with remaining words
  4. Monitor for recurring rate limits
↓
Time wasted: 30 minutes per incident
```

**Scenario 2: Timeout Errors**
```
Image generation request to Leonardo AI
↓
API takes >2 minutes (current timeout)
↓
Session fails with "Timeout"
↓
Developer must:
  1. Check if image was actually generated (might be complete)
  2. Manually query Leonardo API for status
  3. If complete, manually update session data
  4. If not, restart generation
↓
Time wasted: 20 minutes per incident
```

**Scenario 3: Invalid Prompt Errors**
```
Mnemonic generation with special characters
↓
OpenAI rejects prompt: "Invalid request: special character not allowed"
↓
Session fails
↓
Developer must:
  1. Identify problematic character
  2. Sanitize input
  3. Retry request
↓
Time wasted: 15 minutes per incident
```

### Impact Quantification

**Current State:**
- **Session failure rate:** ~30% (primarily API errors)
- **Manual interventions:** ~10 per week
- **Developer time:** ~5 hours/week
- **User frustration:** High (stuck sessions, no visibility)

**With Error Recovery Agent:**
- **Session failure rate:** <5% (only unrecoverable errors)
- **Manual interventions:** <1 per week
- **Developer time:** <0.5 hours/week
- **User frustration:** Low (transparent auto-recovery)

---

## Agent Capabilities

### 1. Error Detection

**What it does:** Intercepts all exceptions and HTTP error responses

**Detects:**
- HTTP errors (4xx, 5xx)
- Timeouts
- Network failures
- JSON parsing errors
- Validation failures

**How:**
```java
@Around("execution(* com.raidrin.eme..*Service.*(..))")
public Object detectErrors(ProceedingJoinPoint joinPoint) {
    try {
        return joinPoint.proceed();
    } catch (Exception e) {
        ErrorContext error = ErrorContext.from(e, joinPoint);
        return agent.handleError(error);
    }
}
```

---

### 2. Error Diagnosis

**What it does:** Classifies errors and determines root causes

**Classification:**
```java
public enum ErrorType {
    // Transient (retry likely to succeed)
    RATE_LIMIT,           // 429 Too Many Requests
    SERVICE_UNAVAILABLE,  // 503 Service Unavailable
    TIMEOUT,              // Request timeout
    NETWORK_ERROR,        // Connection refused, DNS failure

    // Persistent (requires intervention)
    INVALID_PROMPT,       // 400 Bad Request (prompt issue)
    AUTHENTICATION,       // 401 Unauthorized (API key issue)
    INSUFFICIENT_QUOTA,   // 429 with quota message
    NOT_FOUND,            // 404 (endpoint changed?)

    // Internal
    PARSING_ERROR,        // Can't parse API response
    VALIDATION_ERROR,     // Business logic validation failed
    UNKNOWN               // Unclassified
}
```

**Diagnosis Logic:**
```java
public ErrorDiagnosis diagnose(Exception exception, AgentContext context) {
    // HTTP error codes
    if (exception instanceof HttpClientErrorException) {
        HttpStatus status = ((HttpClientErrorException) exception).getStatusCode();

        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            String body = ((HttpClientErrorException) exception).getResponseBodyAsString();
            if (body.contains("quota")) {
                return ErrorDiagnosis.builder()
                    .type(ErrorType.INSUFFICIENT_QUOTA)
                    .severity(Severity.CRITICAL)
                    .recoverable(false)
                    .recommendation("Check billing and increase quota")
                    .build();
            } else {
                return ErrorDiagnosis.builder()
                    .type(ErrorType.RATE_LIMIT)
                    .severity(Severity.MEDIUM)
                    .recoverable(true)
                    .retryAfterSeconds(parseRetryAfter(exception))
                    .recommendation("Exponential backoff retry")
                    .build();
            }
        }
    }

    // Timeout errors
    if (exception instanceof ResourceAccessException &&
        exception.getCause() instanceof SocketTimeoutException) {
        return ErrorDiagnosis.builder()
            .type(ErrorType.TIMEOUT)
            .severity(Severity.MEDIUM)
            .recoverable(true)
            .recommendation("Retry with extended timeout or check if operation completed")
            .build();
    }

    // ... more diagnosis logic
}
```

---

### 3. Recovery Strategy Selection

**What it does:** Chooses optimal recovery approach based on diagnosis

**Strategies:**

#### Strategy 1: Exponential Backoff Retry
**When:** Transient errors (rate limits, timeouts, network issues)
**How:**
```java
public RecoveryStrategy exponentialBackoff() {
    return RecoveryStrategy.builder()
        .name("exponential_backoff")
        .maxAttempts(5)
        .initialDelay(Duration.ofSeconds(1))
        .multiplier(2.0)  // 1s, 2s, 4s, 8s, 16s
        .maxDelay(Duration.ofSeconds(30))
        .jitter(0.1)  // Add 10% randomness to avoid thundering herd
        .build();
}

// Example execution:
int attempt = 0;
Duration delay = strategy.getInitialDelay();
while (attempt < strategy.getMaxAttempts()) {
    try {
        return operation.execute();
    } catch (Exception e) {
        attempt++;
        if (attempt >= strategy.getMaxAttempts()) throw e;

        Thread.sleep(delay.toMillis() + jitter());
        delay = Duration.ofMillis(
            Math.min(
                (long)(delay.toMillis() * strategy.getMultiplier()),
                strategy.getMaxDelay().toMillis()
            )
        );
    }
}
```

#### Strategy 2: Prompt Sanitization & Retry
**When:** Invalid prompt errors (400 with prompt rejection message)
**How:**
```java
public RecoveryStrategy promptSanitization() {
    return RecoveryStrategy.builder()
        .name("prompt_sanitization")
        .transformation(input -> {
            String sanitized = input;
            // Remove special characters that cause issues
            sanitized = sanitized.replaceAll("[^\\p{L}\\p{N}\\s.,!?'-]", "");
            // Trim to max length
            if (sanitized.length() > 4000) {
                sanitized = sanitized.substring(0, 4000);
            }
            // Remove potentially problematic patterns
            sanitized = sanitized.replaceAll("\\{\\{.*?\\}\\}", "");
            return sanitized;
        })
        .maxAttempts(2)
        .build();
}

// Example execution:
String originalPrompt = context.getPrompt();
String sanitized = strategy.getTransformation().apply(originalPrompt);
log.info("Retrying with sanitized prompt", "original_length", originalPrompt.length(),
    "sanitized_length", sanitized.length());
return operation.executeWith(sanitized);
```

#### Strategy 3: Provider Fallback
**When:** Service unavailable, consistent failures from primary provider
**How:**
```java
public RecoveryStrategy providerFallback() {
    return RecoveryStrategy.builder()
        .name("provider_fallback")
        .fallbackChain(List.of(
            ApiProvider.OPENAI_PRIMARY,
            ApiProvider.OPENAI_BACKUP,
            ApiProvider.GOOGLE_TRANSLATE  // For translation fallback
        ))
        .maxAttempts(3)  // One attempt per provider
        .build();
}

// Example execution:
for (ApiProvider provider : strategy.getFallbackChain()) {
    try {
        log.info("Attempting with provider", "provider", provider);
        return operation.executeWith(provider);
    } catch (Exception e) {
        log.warn("Provider failed", "provider", provider, "error", e.getMessage());
        // Continue to next provider
    }
}
throw new AllProvidersFailedException();
```

#### Strategy 4: Graceful Degradation
**When:** All retries exhausted, but partial result is acceptable
**How:**
```java
public RecoveryStrategy gracefulDegradation() {
    return RecoveryStrategy.builder()
        .name("graceful_degradation")
        .fallbackValue(context -> {
            // Return cached value if available
            if (context.hasCachedValue()) {
                log.info("Returning cached value");
                return context.getCachedValue();
            }

            // Return simplified result
            if (context.getOperationType() == OperationType.TRANSLATION) {
                log.info("Returning simple translation");
                return Set.of(simpleTranslationFallback(context.getInput()));
            }

            // Skip optional features
            if (context.isOptional()) {
                log.info("Skipping optional operation");
                return null;  // Caller handles null
            }

            throw new UnrecoverableException("No fallback available");
        })
        .build();
}
```

#### Strategy 5: Async Status Check (for long-running operations)
**When:** Timeout on image generation, but operation might still be running
**How:**
```java
public RecoveryStrategy asyncStatusCheck() {
    return RecoveryStrategy.builder()
        .name("async_status_check")
        .executor((context) -> {
            String operationId = context.getOperationId();
            if (operationId == null) {
                throw new RecoveryException("No operation ID to check");
            }

            // Poll for status
            int maxChecks = 10;
            for (int i = 0; i < maxChecks; i++) {
                StatusResponse status = checkOperationStatus(operationId);

                if (status.isComplete()) {
                    log.info("Operation completed after timeout", "operationId", operationId);
                    return status.getResult();
                }

                if (status.isFailed()) {
                    throw new RecoveryException("Operation failed: " + status.getError());
                }

                Thread.sleep(5000);  // Check every 5 seconds
            }

            throw new RecoveryException("Operation still pending after extended wait");
        })
        .build();
}
```

---

### 4. Strategy Execution

**What it does:** Executes chosen recovery strategy with logging and metrics

**Implementation:**
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartErrorRecoveryAgent implements AgentInterface {
    private final ErrorDiagnosticService diagnosticService;
    private final RecoveryStrategySelector strategySelector;
    private final AgentMetricsService metricsService;
    private final AgentExecutionRepository executionRepository;

    public <T> T executeWithRecovery(Callable<T> operation, AgentContext context) {
        long startTime = System.currentTimeMillis();
        List<RecoveryAttempt> attempts = new ArrayList<>();

        try {
            // Try original operation first
            return operation.call();

        } catch (Exception originalException) {
            log.warn("Operation failed, attempting recovery",
                "operation", context.getOperationName(),
                "error", originalException.getMessage());

            // Diagnose error
            ErrorDiagnosis diagnosis = diagnosticService.diagnose(originalException, context);
            log.info("Error diagnosed", "type", diagnosis.getType(),
                "severity", diagnosis.getSeverity(), "recoverable", diagnosis.isRecoverable());

            if (!diagnosis.isRecoverable()) {
                log.error("Error not recoverable", "diagnosis", diagnosis);
                recordFailure(context, diagnosis, attempts, startTime);
                throw new UnrecoverableException(diagnosis, originalException);
            }

            // Select recovery strategy
            RecoveryStrategy strategy = strategySelector.selectStrategy(diagnosis, context);
            log.info("Selected recovery strategy", "strategy", strategy.getName());

            // Execute recovery
            try {
                T result = executeStrategy(strategy, operation, context, attempts);
                recordSuccess(context, diagnosis, strategy, attempts, startTime);
                return result;

            } catch (Exception recoveryException) {
                log.error("Recovery failed after all attempts",
                    "strategy", strategy.getName(),
                    "attempts", attempts.size());
                recordFailure(context, diagnosis, attempts, startTime);
                throw new RecoveryFailedException(diagnosis, recoveryException, attempts);
            }
        }
    }

    private <T> T executeStrategy(RecoveryStrategy strategy, Callable<T> operation,
                                   AgentContext context, List<RecoveryAttempt> attempts) {
        switch (strategy.getName()) {
            case "exponential_backoff":
                return executeWithBackoff(strategy, operation, attempts);

            case "prompt_sanitization":
                return executeWithSanitization(strategy, operation, context, attempts);

            case "provider_fallback":
                return executeWithFallback(strategy, operation, context, attempts);

            case "graceful_degradation":
                return executeGracefulDegradation(strategy, context);

            case "async_status_check":
                return executeAsyncCheck(strategy, context);

            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy.getName());
        }
    }

    private void recordSuccess(AgentContext context, ErrorDiagnosis diagnosis,
                               RecoveryStrategy strategy, List<RecoveryAttempt> attempts,
                               long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        // Save to database
        AgentExecutionEntity execution = AgentExecutionEntity.builder()
            .agentName("SmartErrorRecoveryAgent")
            .sessionId(context.getSessionId())
            .context(toJson(context))
            .decisions(toJson(Map.of(
                "diagnosis", diagnosis,
                "strategy", strategy.getName(),
                "attempts", attempts
            )))
            .outcome("SUCCESS")
            .durationMs((int)duration)
            .build();
        executionRepository.save(execution);

        // Record metrics
        metricsService.recordAgentExecution("error_recovery", Duration.ofMillis(duration), "success");
        metricsService.incrementCounter("error_recovery.recoveries",
            "error_type", diagnosis.getType().name(),
            "strategy", strategy.getName()
        );

        log.info("Recovery successful",
            "error_type", diagnosis.getType(),
            "strategy", strategy.getName(),
            "attempts", attempts.size(),
            "duration_ms", duration);
    }
}
```

---

### 5. Learning from Patterns

**What it does:** Tracks error patterns and optimizes recovery strategies

**Pattern Detection:**
```java
@Service
@RequiredArgsConstructor
public class ErrorPatternLearningService {
    private final AgentExecutionRepository executionRepository;

    public ErrorPattern analyzePatterns(Duration timeWindow) {
        LocalDateTime since = LocalDateTime.now().minus(timeWindow);

        List<AgentExecutionEntity> executions =
            executionRepository.findByAgentNameAndCreatedAtAfter("SmartErrorRecoveryAgent", since);

        // Group by error type
        Map<ErrorType, List<AgentExecutionEntity>> byType = executions.stream()
            .collect(Collectors.groupingBy(e -> extractErrorType(e)));

        // Analyze success rates per strategy
        Map<String, StrategyEffectiveness> effectiveness = new HashMap<>();
        for (Map.Entry<ErrorType, List<AgentExecutionEntity>> entry : byType.entrySet()) {
            ErrorType errorType = entry.getKey();
            List<AgentExecutionEntity> execs = entry.getValue();

            Map<String, Long> successesByStrategy = execs.stream()
                .filter(e -> "SUCCESS".equals(e.getOutcome()))
                .collect(Collectors.groupingBy(
                    this::extractStrategy,
                    Collectors.counting()
                ));

            Map<String, Long> failuresByStrategy = execs.stream()
                .filter(e -> "FAILURE".equals(e.getOutcome()))
                .collect(Collectors.groupingBy(
                    this::extractStrategy,
                    Collectors.counting()
                ));

            // Calculate effectiveness
            for (String strategy : successesByStrategy.keySet()) {
                long successes = successesByStrategy.getOrDefault(strategy, 0L);
                long failures = failuresByStrategy.getOrDefault(strategy, 0L);
                double successRate = (double)successes / (successes + failures);

                effectiveness.put(errorType + ":" + strategy,
                    StrategyEffectiveness.builder()
                        .errorType(errorType)
                        .strategyName(strategy)
                        .successRate(successRate)
                        .totalAttempts(successes + failures)
                        .build()
                );
            }
        }

        return ErrorPattern.builder()
            .timeWindow(timeWindow)
            .totalErrors(executions.size())
            .errorDistribution(byType.entrySet().stream()
                .collect(Collectors.toMap(
                    e -> e.getKey(),
                    e -> e.getValue().size()
                )))
            .strategyEffectiveness(effectiveness)
            .build();
    }

    public RecoveryStrategy optimizeStrategy(ErrorType errorType, AgentContext context) {
        ErrorPattern patterns = analyzePatterns(Duration.ofDays(7));

        // Find most effective strategy for this error type
        Optional<StrategyEffectiveness> best = patterns.getStrategyEffectiveness().values().stream()
            .filter(e -> e.getErrorType() == errorType)
            .max(Comparator.comparing(StrategyEffectiveness::getSuccessRate));

        if (best.isPresent() && best.get().getSuccessRate() > 0.8) {
            log.info("Using learned optimal strategy",
                "error_type", errorType,
                "strategy", best.get().getStrategyName(),
                "success_rate", best.get().getSuccessRate());

            return buildStrategy(best.get().getStrategyName());
        }

        // Fall back to default strategy
        return defaultStrategyFor(errorType);
    }
}
```

---

## Technical Architecture

### Class Diagram

```
┌─────────────────────────────────────────┐
│      SmartErrorRecoveryAgent            │
│      implements AgentInterface          │
├─────────────────────────────────────────┤
│ + executeWithRecovery<T>(              │
│     operation: Callable<T>,             │
│     context: AgentContext               │
│   ): T                                  │
└────────────┬────────────────────────────┘
             │
             │ uses
             ├──────────────────────────────────────┐
             │                                      │
  ┌──────────▼──────────────┐          ┌───────────▼──────────────┐
  │ ErrorDiagnosticService  │          │ RecoveryStrategySelector │
  ├─────────────────────────┤          ├──────────────────────────┤
  │ + diagnose(             │          │ + selectStrategy(        │
  │     exception,          │          │     diagnosis,           │
  │     context             │          │     context              │
  │   ): ErrorDiagnosis     │          │   ): RecoveryStrategy    │
  └─────────────────────────┘          └──────────────────────────┘
             │                                      │
             │                                      │
  ┌──────────▼──────────────┐          ┌───────────▼──────────────┐
  │    ErrorDiagnosis       │          │    RecoveryStrategy      │
  ├─────────────────────────┤          ├──────────────────────────┤
  │ - type: ErrorType       │          │ - name: String           │
  │ - severity: Severity    │          │ - maxAttempts: int       │
  │ - recoverable: boolean  │          │ - initialDelay: Duration │
  │ - retryAfterSeconds     │          │ - multiplier: double     │
  │ - recommendation        │          │ - fallbackChain: List    │
  └─────────────────────────┘          │ - transformation: Fn     │
                                       └──────────────────────────┘
```

---

### Sequence Diagram: Error Recovery Flow

```
User          Controller        Service        Agent         DiagnosticSvc    StrategySvc     Database
 │                │                │             │                 │               │             │
 │  Submit batch  │                │             │                 │               │             │
 ├───────────────>│                │             │                 │               │             │
 │                │  Process       │             │                 │               │             │
 │                ├───────────────>│             │                 │               │             │
 │                │                │  Translate  │                 │               │             │
 │                │                ├────────────>│                 │               │             │
 │                │                │             │  Call OpenAI    │               │             │
 │                │                │             ├─────────────────X (429 Rate Limit)
 │                │                │             │                 │               │             │
 │                │                │             │  Diagnose       │               │             │
 │                │                │             ├────────────────>│               │             │
 │                │                │             │  ErrorDiagnosis │               │             │
 │                │                │             │<────────────────┤               │             │
 │                │                │             │  (RATE_LIMIT)   │               │             │
 │                │                │             │                 │               │             │
 │                │                │             │  Select Strategy                │             │
 │                │                │             ├─────────────────────────────────>│             │
 │                │                │             │  RecoveryStrategy                │             │
 │                │                │             │<─────────────────────────────────┤             │
 │                │                │             │  (Exponential Backoff)           │             │
 │                │                │             │                 │               │             │
 │                │                │             │  Wait 1s        │               │             │
 │                │                │             ├─────────────────┐               │             │
 │                │                │             │  (sleep)        │               │             │
 │                │                │             │<────────────────┘               │             │
 │                │                │             │  Call OpenAI    │               │             │
 │                │                │             ├─────────────────X (429 again)   │             │
 │                │                │             │                 │               │             │
 │                │                │             │  Wait 2s        │               │             │
 │                │                │             ├─────────────────┐               │             │
 │                │                │             │<────────────────┘               │             │
 │                │                │             │  Call OpenAI    │               │             │
 │                │                │             ├─────────────────> (SUCCESS!)    │             │
 │                │                │             │  Translation    │               │             │
 │                │                │             │<─────────────────┤               │             │
 │                │                │             │                 │               │             │
 │                │                │             │  Record Success                 │             │
 │                │                │             ├─────────────────────────────────────────────────>│
 │                │                │  Result     │                 │               │             │
 │                │                │<────────────┤                 │               │             │
 │                │  Response      │             │                 │               │             │
 │                │<───────────────┤             │                 │               │             │
 │  Success       │                │             │                 │               │             │
 │<───────────────┤                │             │                 │               │             │
```

---

## Implementation Specification

### File Structure

```
src/main/java/com/raidrin/eme/agent/
├── core/
│   ├── AgentInterface.java
│   ├── BaseAgent.java
│   ├── AgentContext.java
│   └── AgentResult.java
├── recovery/
│   ├── SmartErrorRecoveryAgent.java
│   ├── ErrorDiagnosticService.java
│   ├── RecoveryStrategySelector.java
│   ├── StrategyExecutor.java
│   ├── ErrorPatternLearningService.java
│   ├── model/
│   │   ├── ErrorType.java
│   │   ├── ErrorDiagnosis.java
│   │   ├── RecoveryStrategy.java
│   │   ├── RecoveryAttempt.java
│   │   ├── ErrorPattern.java
│   │   └── StrategyEffectiveness.java
│   └── strategy/
│       ├── ExponentialBackoffStrategy.java
│       ├── PromptSanitizationStrategy.java
│       ├── ProviderFallbackStrategy.java
│       ├── GracefulDegradationStrategy.java
│       └── AsyncStatusCheckStrategy.java
├── config/
│   ├── AgentConfiguration.java
│   └── AgentProperties.java
├── metrics/
│   └── AgentMetricsService.java
└── repository/
    └── AgentExecutionRepository.java
```

### Core Interfaces

**AgentInterface.java**
```java
package com.raidrin.eme.agent.core;

public interface AgentInterface {
    /**
     * Get the agent's unique name
     */
    String getName();

    /**
     * Execute the agent's primary function
     */
    <T> AgentResult<T> execute(AgentContext context);

    /**
     * Check if agent can handle this context
     */
    boolean canHandle(AgentContext context);

    /**
     * Get agent's current configuration
     */
    Map<String, Object> getConfiguration();
}
```

**AgentContext.java**
```java
package com.raidrin.eme.agent.core;

@Data
@Builder
public class AgentContext {
    private Long sessionId;
    private String operationName;
    private OperationType operationType;
    private Map<String, Object> parameters;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;

    // Convenience methods
    public <T> T getParameter(String key, Class<T> type) {
        return type.cast(parameters.get(key));
    }

    public boolean hasParameter(String key) {
        return parameters != null && parameters.containsKey(key);
    }

    public String getPrompt() {
        return getParameter("prompt", String.class);
    }

    public String getOperationId() {
        return getParameter("operation_id", String.class);
    }

    public static AgentContext from(ProceedingJoinPoint joinPoint) {
        // Extract context from AOP join point
        String operationName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        Map<String, Object> parameters = new HashMap<>();
        // Extract parameters from method arguments
        // ...

        return AgentContext.builder()
            .operationName(operationName)
            .parameters(parameters)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
```

---

### Main Agent Implementation

**SmartErrorRecoveryAgent.java** (full implementation)

```java
package com.raidrin.eme.agent.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.raidrin.eme.agent.core.*;
import java.util.concurrent.Callable;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartErrorRecoveryAgent implements AgentInterface {
    private final ErrorDiagnosticService diagnosticService;
    private final RecoveryStrategySelector strategySelector;
    private final StrategyExecutor strategyExecutor;
    private final ErrorPatternLearningService learningService;
    private final AgentMetricsService metricsService;
    private final AgentExecutionRepository executionRepository;
    private final AgentProperties agentProperties;

    @Override
    public String getName() {
        return "SmartErrorRecoveryAgent";
    }

    @Override
    public <T> AgentResult<T> execute(AgentContext context) {
        throw new UnsupportedOperationException(
            "Use executeWithRecovery(Callable, AgentContext) instead"
        );
    }

    @Override
    public boolean canHandle(AgentContext context) {
        return agentProperties.getErrorRecovery().isEnabled();
    }

    @Override
    public Map<String, Object> getConfiguration() {
        return Map.of(
            "enabled", agentProperties.getErrorRecovery().isEnabled(),
            "maxRetries", agentProperties.getErrorRecovery().getMaxRetries(),
            "backoffMultiplier", agentProperties.getErrorRecovery().getBackoffMultiplier()
        );
    }

    /**
     * Primary method: Execute operation with automatic error recovery
     */
    public <T> T executeWithRecovery(Callable<T> operation, AgentContext context) {
        if (!canHandle(context)) {
            log.debug("Error recovery disabled, executing without agent");
            try {
                return operation.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        long startTime = System.currentTimeMillis();
        List<RecoveryAttempt> attempts = new ArrayList<>();

        try {
            // Attempt original operation
            log.debug("Executing operation", "operation", context.getOperationName());
            return operation.call();

        } catch (Exception originalException) {
            return handleError(originalException, operation, context, attempts, startTime);
        }
    }

    private <T> T handleError(Exception originalException, Callable<T> operation,
                               AgentContext context, List<RecoveryAttempt> attempts,
                               long startTime) {
        log.warn("Operation failed, initiating error recovery",
            "operation", context.getOperationName(),
            "error_message", originalException.getMessage(),
            "error_class", originalException.getClass().getSimpleName());

        // Step 1: Diagnose error
        ErrorDiagnosis diagnosis = diagnosticService.diagnose(originalException, context);
        log.info("Error diagnosed",
            "error_type", diagnosis.getType(),
            "severity", diagnosis.getSeverity(),
            "recoverable", diagnosis.isRecoverable(),
            "recommendation", diagnosis.getRecommendation());

        // Step 2: Check if recoverable
        if (!diagnosis.isRecoverable()) {
            log.error("Error is not recoverable", "diagnosis", diagnosis);
            recordFailure(context, diagnosis, attempts, startTime);
            throw new UnrecoverableException(diagnosis, originalException);
        }

        // Step 3: Select recovery strategy (with learning)
        RecoveryStrategy strategy = learningService.optimizeStrategy(diagnosis.getType(), context);
        if (strategy == null) {
            strategy = strategySelector.selectStrategy(diagnosis, context);
        }
        log.info("Selected recovery strategy", "strategy", strategy.getName());

        // Step 4: Execute recovery
        try {
            T result = strategyExecutor.execute(strategy, operation, context, attempts);
            recordSuccess(context, diagnosis, strategy, attempts, startTime);
            return result;

        } catch (Exception recoveryException) {
            log.error("Recovery failed after all attempts",
                "strategy", strategy.getName(),
                "total_attempts", attempts.size(),
                "error", recoveryException.getMessage());
            recordFailure(context, diagnosis, attempts, startTime);
            throw new RecoveryFailedException(diagnosis, recoveryException, attempts);
        }
    }

    private void recordSuccess(AgentContext context, ErrorDiagnosis diagnosis,
                               RecoveryStrategy strategy, List<RecoveryAttempt> attempts,
                               long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        // Save execution record
        AgentExecutionEntity execution = AgentExecutionEntity.builder()
            .agentName(getName())
            .sessionId(context.getSessionId())
            .context(toJson(context))
            .decisions(toJson(Map.of(
                "original_error_type", diagnosis.getType(),
                "recovery_strategy", strategy.getName(),
                "attempts", attempts.stream()
                    .map(a -> Map.of(
                        "attempt_number", a.getAttemptNumber(),
                        "delay_ms", a.getDelayMs(),
                        "outcome", a.getOutcome()
                    ))
                    .collect(Collectors.toList())
            )))
            .outcome("SUCCESS")
            .durationMs((int)duration)
            .createdAt(LocalDateTime.now())
            .build();
        executionRepository.save(execution);

        // Record metrics
        metricsService.recordAgentExecution(getName(), Duration.ofMillis(duration), "success");
        metricsService.incrementCounter("agent.recovery.success",
            "error_type", diagnosis.getType().name(),
            "strategy", strategy.getName(),
            "attempts", String.valueOf(attempts.size())
        );

        log.info("Recovery successful",
            "error_type", diagnosis.getType(),
            "strategy", strategy.getName(),
            "total_attempts", attempts.size(),
            "duration_ms", duration);
    }

    private void recordFailure(AgentContext context, ErrorDiagnosis diagnosis,
                               List<RecoveryAttempt> attempts, long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        AgentExecutionEntity execution = AgentExecutionEntity.builder()
            .agentName(getName())
            .sessionId(context.getSessionId())
            .context(toJson(context))
            .decisions(toJson(Map.of(
                "error_type", diagnosis.getType(),
                "error_severity", diagnosis.getSeverity(),
                "recoverable", diagnosis.isRecoverable(),
                "attempts", attempts.size()
            )))
            .outcome("FAILURE")
            .durationMs((int)duration)
            .createdAt(LocalDateTime.now())
            .build();
        executionRepository.save(execution);

        metricsService.recordAgentExecution(getName(), Duration.ofMillis(duration), "failure");
        metricsService.incrementCounter("agent.recovery.failure",
            "error_type", diagnosis.getType().name(),
            "reason", diagnosis.isRecoverable() ? "max_retries_exceeded" : "not_recoverable"
        );
    }

    private String toJson(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
```

---

## Database Schema Changes

### Flyway Migration: V10__create_agent_tables.sql

```sql
-- Agent execution tracking
CREATE TABLE agent_executions (
    id BIGSERIAL PRIMARY KEY,
    agent_name VARCHAR(100) NOT NULL,
    session_id BIGINT,
    context JSONB,
    decisions JSONB,
    outcome VARCHAR(50) NOT NULL,  -- SUCCESS, FAILURE
    duration_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_agent_session
        FOREIGN KEY (session_id)
        REFERENCES translation_sessions(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_agent_executions_agent_name
    ON agent_executions(agent_name);

CREATE INDEX idx_agent_executions_session_id
    ON agent_executions(session_id);

CREATE INDEX idx_agent_executions_created_at
    ON agent_executions(created_at DESC);

CREATE INDEX idx_agent_executions_outcome
    ON agent_executions(outcome);

-- GIN index for JSON querying
CREATE INDEX idx_agent_executions_decisions
    ON agent_executions USING GIN (decisions);

COMMENT ON TABLE agent_executions IS 'Tracks all agent executions with decisions and outcomes';
COMMENT ON COLUMN agent_executions.context IS 'Input context provided to the agent (operation details, parameters)';
COMMENT ON COLUMN agent_executions.decisions IS 'Agent decisions made during execution (diagnosis, strategy, attempts)';
```

---

## Integration Pattern

### Option 1: AOP Aspect (Recommended)

**AgentAspect.java**
```java
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentAspect {
    private final SmartErrorRecoveryAgent errorRecoveryAgent;

    /**
     * Wrap methods annotated with @AgentManaged in error recovery
     */
    @Around("@annotation(com.raidrin.eme.agent.annotation.AgentManaged)")
    public Object applyAgentRecovery(ProceedingJoinPoint joinPoint) throws Throwable {
        AgentContext context = AgentContext.from(joinPoint);

        return errorRecoveryAgent.executeWithRecovery(() -> {
            return joinPoint.proceed();
        }, context);
    }
}
```

**Usage in existing services:**
```java
@Service
@RequiredArgsConstructor
public class OpenAITranslationService implements TranslationService {

    @AgentManaged  // <-- Add this annotation
    @Override
    public Set<String> translate(String text, String sourceLanguage, String targetLanguage) {
        // Existing implementation unchanged
        // Agent automatically handles any exceptions
    }
}
```

---

### Option 2: Explicit Wrapping

**Modify existing services directly:**
```java
@Service
@RequiredArgsConstructor
public class OpenAITranslationService implements TranslationService {
    private final SmartErrorRecoveryAgent errorRecoveryAgent;

    @Override
    public Set<String> translate(String text, String sourceLanguage, String targetLanguage) {
        AgentContext context = AgentContext.builder()
            .operationName("translate")
            .operationType(OperationType.TRANSLATION)
            .parameters(Map.of(
                "text", text,
                "source", sourceLanguage,
                "target", targetLanguage
            ))
            .build();

        return errorRecoveryAgent.executeWithRecovery(() -> {
            return performTranslation(text, sourceLanguage, targetLanguage);
        }, context);
    }

    private Set<String> performTranslation(String text, String source, String target) {
        // Original translation logic here
    }
}
```

---

## Testing Strategy

### Unit Tests

**SmartErrorRecoveryAgentTest.java**
```java
@SpringBootTest
class SmartErrorRecoveryAgentTest {

    @MockBean
    private ErrorDiagnosticService diagnosticService;

    @MockBean
    private RecoveryStrategySelector strategySelector;

    @Autowired
    private SmartErrorRecoveryAgent agent;

    @Test
    void shouldRecoverFromRateLimit() {
        // Arrange
        AtomicInteger callCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            if (callCount.incrementAndGet() <= 2) {
                throw new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
            }
            return "success";
        };

        AgentContext context = AgentContext.builder()
            .operationName("test")
            .build();

        when(diagnosticService.diagnose(any(), any()))
            .thenReturn(ErrorDiagnosis.builder()
                .type(ErrorType.RATE_LIMIT)
                .recoverable(true)
                .build());

        when(strategySelector.selectStrategy(any(), any()))
            .thenReturn(RecoveryStrategy.exponentialBackoff());

        // Act
        String result = agent.executeWithRecovery(operation, context);

        // Assert
        assertEquals("success", result);
        assertEquals(3, callCount.get());
        verify(diagnosticService, times(1)).diagnose(any(), any());
    }

    @Test
    void shouldNotRecoverFromUnrecoverableError() {
        // Arrange
        Callable<String> operation = () -> {
            throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        };

        when(diagnosticService.diagnose(any(), any()))
            .thenReturn(ErrorDiagnosis.builder()
                .type(ErrorType.AUTHENTICATION)
                .recoverable(false)
                .build());

        // Act & Assert
        assertThrows(UnrecoverableException.class, () -> {
            agent.executeWithRecovery(operation, AgentContext.builder().build());
        });
    }
}
```

---

### Integration Tests

**ErrorRecoveryIntegrationTest.java**
```java
@SpringBootTest
@AutoConfigureMockMvc
class ErrorRecoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RestTemplate restTemplate;

    @Test
    void shouldAutoRecoverFromRateLimitInRealScenario() throws Exception {
        // Simulate rate limit then success
        when(restTemplate.exchange(anyString(), any(), any(), eq(OpenAiResponse.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))
            .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))
            .thenReturn(ResponseEntity.ok(mockOpenAiResponse("bonjour")));

        // Make request
        mockMvc.perform(post("/api/translate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"word\":\"hello\",\"sourceLang\":\"en\",\"targetLang\":\"fr\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.translations[0]").value("bonjour"));

        // Verify retries occurred
        verify(restTemplate, times(3)).exchange(anyString(), any(), any(), any());
    }
}
```

---

## Metrics & Monitoring

### Prometheus Metrics

```
# Recovery attempts
agent_recovery_attempts_total{error_type="RATE_LIMIT",strategy="exponential_backoff",outcome="success"} 42
agent_recovery_attempts_total{error_type="TIMEOUT",strategy="async_status_check",outcome="success"} 15

# Recovery duration
agent_recovery_duration_seconds{error_type="RATE_LIMIT",strategy="exponential_backoff"} 2.5

# Recovery success rate
agent_recovery_success_rate{error_type="RATE_LIMIT"} 0.95
```

### Grafana Dashboard

**Key Panels:**
1. Recovery success rate over time (line graph)
2. Error type distribution (pie chart)
3. Most effective strategies (bar chart)
4. Recovery duration percentiles (heatmap)
5. Unrecoverable errors alert (counter with threshold)

---

## Rollout Plan

### Week 1: Development
- Implement core agent classes
- Add database migrations
- Write unit tests
- Configure logging and metrics

### Week 2: Integration & Testing
- Integrate with one service (OpenAITranslationService)
- Run integration tests
- Test all error scenarios
- Deploy to staging with feature flag

### Week 3: Gradual Production Rollout
- Enable for 10% of sessions (feature flag)
- Monitor metrics closely
- Gradually increase to 50%, then 100%
- Document learnings

### Week 4: Refinement
- Analyze error patterns
- Optimize recovery strategies
- Add additional error types if discovered
- Full rollout complete

---

[Next: Quality Assurance Agent →](04-agent-quality-assurance.md)

[← Back to Main Document](../../AGENTIC_AI_INTEGRATION.md)
