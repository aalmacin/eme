# Agent #4: Cost Optimization Agent

[← Back to Main Document](../../AGENTIC_AI_INTEGRATION.md) | [← Previous: Multi-Modal Coherence](05-agent-multimodal-coherence.md) | [Next: Personalization Agent →](07-agent-personalization.md)

---

## Executive Summary

The **Cost Optimization Agent** intelligently selects AI models and providers based on task complexity, batches requests efficiently, monitors spending, and enforces budgets—reducing AI API costs by 30-40% while maintaining quality.

**Priority:** MEDIUM (Phase 3)
**Estimated Effort:** 45 hours
**Expected ROI:** $400-600/month cost savings (assuming $1,200/month baseline)

---

## Problem Statement

### Current State: Fixed Model Selection

**Issues:**
- Always uses `gpt-4o-mini` regardless of task difficulty
- Simple translations (e.g., "hello" → "bonjour") use same model as complex idiomatic phrases
- No provider comparison or fallback
- No cost tracking or budgets
- Inefficient request batching

**Example of Waste:**
```
Task: Translate "hello" from English to Spanish
Current: GPT-4o-mini call (~$0.00015)
Optimal: Simple dictionary lookup or cheaper model (~$0.00003)
Waste: 5x cost for trivial task

Over 1000 simple translations/month: $150 wasted
```

---

## Agent Capabilities

### 1. Intelligent Model Selection

**Strategy:** Choose cheapest model that meets quality requirements

**Decision Matrix:**
```java
public ModelSelection selectOptimalModel(TaskContext task) {
    TaskComplexity complexity = assessComplexity(task);

    switch (complexity) {
        case TRIVIAL:
            // Simple word translations, common phrases
            return ModelSelection.builder()
                .provider(Provider.OPENAI)
                .model("gpt-4o-mini")
                .estimatedCost(0.00015)
                .reasoning("Trivial task, cheapest model sufficient")
                .build();

        case SIMPLE:
            // Standard translations, basic sentences
            return ModelSelection.builder()
                .provider(Provider.OPENAI)
                .model("gpt-4o-mini")
                .estimatedCost(0.00015)
                .build();

        case MODERATE:
            // Idiomatic phrases, cultural nuances
            return ModelSelection.builder()
                .provider(Provider.OPENAI)
                .model("gpt-4o")
                .estimatedCost(0.00050)
                .reasoning("Nuanced task requires more capable model")
                .build();

        case COMPLEX:
            // Creative mnemonics, complex reasoning
            return ModelSelection.builder()
                .provider(Provider.ANTHROPIC)
                .model("claude-3-5-sonnet")
                .estimatedCost(0.00150)
                .reasoning("Complex creative task, premium model justified")
                .build();

        default:
            return defaultModel();
    }
}

private TaskComplexity assessComplexity(TaskContext task) {
    int complexityScore = 0;

    // Word length
    if (task.getInputLength() > 50) complexityScore += 1;

    // Language pair difficulty
    if (isDistantLanguagePair(task.getSourceLang(), task.getTargetLang())) {
        complexityScore += 2;
    }

    // Task type
    switch (task.getTaskType()) {
        case TRANSLATION:
            complexityScore += 0;
            break;
        case SENTENCE_GENERATION:
            complexityScore += 1;
            break;
        case MNEMONIC_GENERATION:
            complexityScore += 2;
            break;
    }

    // Special characters or formatting
    if (task.getInput().matches(".*[^\\p{L}\\p{N}\\s].*")) {
        complexityScore += 1;
    }

    // Map score to complexity
    if (complexityScore <= 1) return TaskComplexity.TRIVIAL;
    if (complexityScore <= 3) return TaskComplexity.SIMPLE;
    if (complexityScore <= 5) return TaskComplexity.MODERATE;
    return TaskComplexity.COMPLEX;
}
```

---

### 2. Request Batching Optimization

**Strategy:** Combine multiple requests to reduce overhead

**Implementation:**
```java
@Service
@RequiredArgsConstructor
public class BatchOptimizationService {
    private final Map<String, List<PendingRequest>> batchQueues = new ConcurrentHashMap<>();

    /**
     * Add request to batch queue instead of immediate execution
     */
    public CompletableFuture<TranslationResult> queueTranslation(TranslationRequest request) {
        String batchKey = getBatchKey(request);  // e.g., "openai:gpt-4o-mini:translation"

        PendingRequest pending = new PendingRequest(request);
        batchQueues.computeIfAbsent(batchKey, k -> new ArrayList<>()).add(pending);

        // Trigger batch processing if queue full or timeout
        scheduleBatchProcessing(batchKey);

        return pending.getFuture();
    }

    private void scheduleBatchProcessing(String batchKey) {
        List<PendingRequest> queue = batchQueues.get(batchKey);

        // Process batch if: queue full OR oldest request waiting >2 seconds
        if (queue.size() >= BATCH_SIZE ||
            queue.get(0).getAge().toMillis() > 2000) {

            CompletableFuture.runAsync(() -> processBatch(batchKey));
        }
    }

    private void processBatch(String batchKey) {
        List<PendingRequest> requests = batchQueues.remove(batchKey);
        if (requests == null || requests.isEmpty()) return;

        log.info("Processing batch", "batch_key", batchKey, "size", requests.size());

        // Combine into single API call
        String combinedPrompt = buildCombinedPrompt(requests);

        try {
            String response = llmService.call(combinedPrompt);
            List<String> results = parseResults(response, requests.size());

            // Distribute results to individual futures
            for (int i = 0; i < requests.size(); i++) {
                requests.get(i).getFuture().complete(results.get(i));
            }

        } catch (Exception e) {
            // Fail all requests in batch
            requests.forEach(r -> r.getFuture().completeExceptionally(e));
        }
    }
}
```

---

### 3. Cost Tracking & Budgets

**Features:**
- Track cost per API call
- Aggregate by provider, model, task type
- Daily/monthly spending limits
- Alerts when approaching budget

**Implementation:**
```java
@Service
@RequiredArgsConstructor
public class CostTrackingService {
    private final ApiCallRepository apiCallRepository;
    private final AlertService alertService;
    private final AgentProperties agentProperties;

    public void recordApiCall(ApiCallContext context, ApiCallResult result) {
        // Calculate cost
        double cost = calculateCost(context.getProvider(), context.getModel(),
            result.getTokensUsed());

        // Save to database
        ApiCallEntity call = ApiCallEntity.builder()
            .sessionId(context.getSessionId())
            .provider(context.getProvider().name())
            .model(context.getModel())
            .operation(context.getOperation())
            .tokensUsed(result.getTokensUsed())
            .estimatedCostUsd(BigDecimal.valueOf(cost))
            .createdAt(LocalDateTime.now())
            .build();
        apiCallRepository.save(call);

        // Check against budget
        checkBudget(cost);

        // Record metrics
        metricsService.recordGauge("api.cost.call", cost,
            "provider", context.getProvider().name(),
            "model", context.getModel());
    }

    private void checkBudget(double additionalCost) {
        BigDecimal dailyBudget = agentProperties.getCostOptimization().getBudgetDailyUsd();
        if (dailyBudget == null) return;

        // Get today's spending
        BigDecimal todaySpend = getTodaysSpending();
        BigDecimal projectedSpend = todaySpend.add(BigDecimal.valueOf(additionalCost));

        // Alert if approaching limit
        if (projectedSpend.compareTo(dailyBudget.multiply(BigDecimal.valueOf(0.8))) > 0) {
            alertService.sendAlert(Alert.builder()
                .level(AlertLevel.WARNING)
                .message(String.format("Approaching daily budget: $%.2f / $%.2f",
                    projectedSpend, dailyBudget))
                .build());
        }

        // Block if over limit
        if (projectedSpend.compareTo(dailyBudget) > 0) {
            throw new BudgetExceededException(
                String.format("Daily budget exceeded: $%.2f / $%.2f", projectedSpend, dailyBudget)
            );
        }
    }

    private BigDecimal getTodaysSpending() {
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        return apiCallRepository.sumCostSince(startOfDay);
    }

    private double calculateCost(Provider provider, String model, int tokens) {
        // Pricing as of 2025 (adjust as needed)
        Map<String, Double> pricing = Map.of(
            "gpt-4o-mini", 0.000150 / 1000,      // per 1K input tokens
            "gpt-4o", 0.002500 / 1000,
            "claude-3-5-sonnet", 0.003000 / 1000,
            "dall-e-3", 0.040000                  // per image
        );

        return pricing.getOrDefault(model, 0.001) * tokens;
    }
}
```

---

### 4. Provider Comparison & Selection

**Strategy:** Periodically A/B test providers for same task, select cheapest with acceptable quality

**Implementation:**
```java
@Service
@RequiredArgsConstructor
public class ProviderOptimizationService {
    private final Map<String, ProviderPerformance> performanceCache = new ConcurrentHashMap<>();

    /**
     * Select best provider based on historical performance
     */
    public Provider selectProvider(TaskContext task) {
        String taskType = task.getTaskType().name();

        // Get performance data for this task type
        ProviderPerformance openAI = performanceCache.get("openai:" + taskType);
        ProviderPerformance anthropic = performanceCache.get("anthropic:" + taskType);

        if (openAI == null || anthropic == null) {
            return Provider.OPENAI;  // Default
        }

        // Compare cost-effectiveness (quality / cost)
        double openAICostEffectiveness = openAI.getQualityScore() / openAI.getAverageCost();
        double anthropicCostEffectiveness = anthropic.getQualityScore() / anthropic.getAverageCost();

        if (anthropicCostEffectiveness > openAICostEffectiveness * 1.1) {
            return Provider.ANTHROPIC;
        }

        return Provider.OPENAI;
    }

    /**
     * Periodically run A/B tests to update performance data
     */
    @Scheduled(fixedDelay = 3600000)  // Every hour
    public void runProviderComparison() {
        // Sample recent tasks
        List<TaskContext> sampleTasks = getRecentRepresentativeTasks(10);

        for (TaskContext task : sampleTasks) {
            // Run with multiple providers
            Map<Provider, ProviderResult> results = new HashMap<>();

            for (Provider provider : Provider.values()) {
                try {
                    ProviderResult result = executeWithProvider(task, provider);
                    results.put(provider, result);
                } catch (Exception e) {
                    log.warn("Provider failed during comparison", "provider", provider, "error", e.getMessage());
                }
            }

            // Update performance metrics
            updateProviderPerformance(task.getTaskType(), results);
        }
    }

    private void updateProviderPerformance(TaskType taskType,
                                           Map<Provider, ProviderResult> results) {
        for (Map.Entry<Provider, ProviderResult> entry : results.entrySet()) {
            String key = entry.getKey().name().toLowerCase() + ":" + taskType.name();

            ProviderPerformance current = performanceCache.getOrDefault(key,
                ProviderPerformance.empty());

            // Update moving averages
            ProviderPerformance updated = current.update(
                entry.getValue().getCost(),
                entry.getValue().getQualityScore(),
                entry.getValue().getLatency()
            );

            performanceCache.put(key, updated);
        }
    }
}
```

---

### 5. Caching Strategy Optimization

**Strategy:** Intelligently cache based on frequency and cost

**Implementation:**
```java
public enum CachingStrategy {
    ALWAYS,      // Common words, high frequency
    CONDITIONAL, // Cost > $0.001 and frequency > 2
    NEVER        // Rare, cheap operations
}

public CachingStrategy determineCachingStrategy(TaskContext task, double estimatedCost) {
    // High-cost operations always cache
    if (estimatedCost > 0.01) {
        return CachingStrategy.ALWAYS;
    }

    // Check historical frequency
    int frequency = getHistoricalFrequency(task.getInput(), task.getTaskType());

    if (frequency > 2 && estimatedCost > 0.001) {
        return CachingStrategy.CONDITIONAL;
    }

    // Very cheap, rare operations not worth caching overhead
    return CachingStrategy.NEVER;
}
```

---

## Technical Architecture

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class CostOptimizationAgent implements AgentInterface {
    private final ModelSelectionService modelSelector;
    private final BatchOptimizationService batchOptimizer;
    private final CostTrackingService costTracker;
    private final ProviderOptimizationService providerOptimizer;
    private final AgentProperties agentProperties;

    @Override
    public String getName() {
        return "CostOptimizationAgent";
    }

    /**
     * Execute operation with cost optimization
     */
    public <T> T executeOptimized(Operation<T> operation, TaskContext task) {
        if (!agentProperties.getCostOptimization().isEnabled()) {
            return operation.execute();
        }

        long startTime = System.currentTimeMillis();

        try {
            // 1. Select optimal model
            ModelSelection model = modelSelector.selectOptimalModel(task);
            log.info("Selected model", "model", model.getModel(),
                "estimated_cost", model.getEstimatedCost(),
                "reasoning", model.getReasoning());

            // 2. Check if should batch
            if (shouldBatch(task)) {
                return batchOptimizer.queueOperation(operation, task, model).get();
            }

            // 3. Execute with selected model
            T result = operation.executeWith(model);

            // 4. Track cost
            ApiCallResult callResult = extractApiCallInfo(result);
            costTracker.recordApiCall(
                ApiCallContext.from(task, model),
                callResult
            );

            return result;

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordAgentExecution(getName(), Duration.ofMillis(duration), "success");
        }
    }
}
```

---

## Database Schema

### V13__create_cost_tracking_tables.sql

```sql
CREATE TABLE api_calls (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    tokens_used INT,
    estimated_cost_usd DECIMAL(10,6) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (session_id) REFERENCES translation_sessions(id) ON DELETE SET NULL
);

CREATE INDEX idx_api_calls_created_at ON api_calls(created_at DESC);
CREATE INDEX idx_api_calls_provider ON api_calls(provider);
CREATE INDEX idx_api_calls_model ON api_calls(model);

CREATE TABLE daily_cost_summary (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL UNIQUE,
    total_cost_usd DECIMAL(10,2) NOT NULL,
    call_count INT NOT NULL,
    by_provider JSONB,
    by_model JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_daily_cost_summary_date ON daily_cost_summary(date DESC);
```

---

## Success Metrics

- ✅ 30-40% reduction in monthly AI API costs
- ✅ Cost per session <$0.15 (down from ~$0.22)
- ✅ No quality degradation (maintain >0.80 quality score)
- ✅ 95% of requests stay within daily budget

**Monthly Savings Projection:**
```
Baseline: $1,200/month
After optimization: $720-840/month
Annual savings: $4,320-5,760
```

---

[Next: Personalization Agent →](07-agent-personalization.md)

[← Back to Main Document](../../AGENTIC_AI_INTEGRATION.md)
