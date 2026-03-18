# AI Selenium Recovery

**AI-powered automatic test recovery for Selenium WebDriver using Playwright MCP and Google Gemini.**

When a Selenium test fails (element not found, stale element, timeout, etc.), the AI agent automatically connects to the same Chrome browser, analyzes the page via accessibility snapshots, and autonomously recovers the test -- no manual intervention needed.

---

## How It Works

```
                          Normal Selenium Test Flow
                          ========================

  Cucumber Step  -->  Page Object Method  -->  Selenium Action  -->  PASS
                                                      |
                                                   FAILURE
                                                      |
                                          ┌───────────▼───────────┐
                                          │  @AIRecover Intercept │
                                          │  (ByteBuddy Advice)   │
                                          └───────────┬───────────┘
                                                      │
                                          ┌───────────▼───────────┐
                                          │  Connect to Chrome    │
                                          │  via CDP (same        │
                                          │  browser session)     │
                                          └───────────┬───────────┘
                                                      │
                                          ┌───────────▼───────────┐
                                          │  Observe-Act Loop:    │
                                          │  1. Accessibility     │
                                          │     Snapshot          │
                                          │  2. Send to Gemini AI │
                                          │  3. Execute Actions   │
                                          │  4. Re-snapshot       │
                                          │  5. Repeat until done │
                                          └───────────┬───────────┘
                                                      │
                                                   RECOVERED
                                                      |
                                              Test Continues ✓
```

### The Complete Flow

1. **Test Setup** -- `TestRunner.@BeforeSuite` calls `AIRecoveryAgent.install()`, which uses ByteBuddy to transform all `@AIRecover` methods at bytecode level
2. **Test Execution** -- Cucumber runs scenarios normally, step definitions call page object methods
3. **Failure Occurs** -- Selenium throws (e.g., `NoSuchElementException`)
4. **ByteBuddy Catches It** -- `AIRecoveryAdvice.onExit()` intercepts the exception
5. **Recovery Starts** -- `AIRecoveryHandler` builds a recovery prompt from the annotation + parameters
6. **Multi-turn Observe-Act Loop**:
   - Connects to Chrome via CDP (Chrome DevTools Protocol)
   - Takes accessibility snapshot of current page
   - Sends snapshot to Gemini AI: *"What should I do to achieve this intent?"*
   - Executes AI-suggested actions (click, type, navigate, etc.)
   - Takes fresh snapshot of the (possibly changed) page
   - Repeats until AI reports intent achieved or max steps reached
7. **Recovery Succeeds** -- Exception is suppressed, test continues normally
8. **Data Extraction** -- If `@AIExtract` is specified, values are extracted from the page post-recovery

---

## Key Features

| Feature | Description |
|---------|-------------|
| **Zero Code Changes** | Just add `@AIRecover` annotation to existing methods -- no lambdas, no wrappers |
| **Multi-Page Recovery** | Handles page transitions via multi-turn observe-act loop |
| **Data Extraction** | Extract values (amounts, IDs, text) from page after recovery with `@AIExtract` |
| **Parameter-Aware** | AI gets method parameter values for precise recovery (e.g., card numbers, names) |
| **Configurable** | Max retries, max steps, validation text -- all configurable |
| **Failsafe** | Recovery failure doesn't break test reporting -- original error preserved |

---

## Project Structure

```
AI-Selenium-Recovery/
├── pom.xml                           # Maven config with all dependencies
├── configuration.properties          # Runtime settings (API keys, timeouts)
├── README.md
│
└── src/test/java/com/ai/selenium/recovery/
    │
    ├── annotations/                  # The magic annotations
    │   ├── AIRecover.java            # Mark methods for automatic AI recovery
    │   └── AIExtract.java            # Extract data from page after recovery
    │
    ├── agent/                        # ByteBuddy instrumentation
    │   ├── AIRecoveryAgent.java      # Installs the bytecode transformer
    │   ├── AIRecoveryAdvice.java     # Inlined try-catch advice
    │   └── AIRecoveryHandler.java    # Builds prompts, coordinates recovery
    │
    ├── orchestrator/                 # Recovery engine
    │   └── PlaywrightRecoveryOrchestrator.java  # Multi-turn observe-act loop
    │
    ├── client/                       # External integrations
    │   ├── PlaywrightAIRecoveryClient.java  # Gemini AI decision engine
    │   ├── PlaywrightMCPClient.java         # Playwright MCP browser client
    │   └── MCPJsonRpcClient.java            # JSON-RPC 2.0 protocol handler
    │
    ├── core/                         # Framework utilities
    │   ├── DriverManager.java        # Thread-safe WebDriver management
    │   ├── PropertyUtils.java        # Configuration reader
    │   ├── DataStore.java            # Key-value store for extracted data
    │   └── LogUtil.java              # Centralized logging
    │
    ├── pages/                        # Demo page objects
    │   ├── BasePage.java             # Common Selenium utilities
    │   ├── SearchPage.java           # Google Search demo
    │   ├── LoginPage.java            # Login form demo
    │   └── CheckoutPage.java         # Payment checkout demo
    │
    ├── steps/                        # Cucumber step definitions
    │   ├── Hooks.java                # Before/After hooks
    │   ├── SearchStepDefs.java
    │   └── CheckoutStepDefs.java
    │
    └── runner/
        └── TestRunner.java           # Cucumber + TestNG runner
```

---

## Quick Start

### Prerequisites

- **Java 21+**
- **Maven 3.8+**
- **Node.js 18+** (for Playwright MCP: `npx @playwright/mcp@latest`)
- **Google Gemini API Key** ([Get one here](https://aistudio.google.com/apikey))

### Setup

1. **Clone the repo:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/AI-Selenium-Recovery.git
   cd AI-Selenium-Recovery
   ```

2. **Set your Gemini API key** in `configuration.properties`:
   ```properties
   GEMINI_API_KEY=your_actual_key_here
   ```
   Or via environment variable:
   ```bash
   export GEMINI_API_KEY=your_actual_key_here
   ```

3. **Run the tests:**
   ```bash
   mvn clean test
   ```

---

## Usage Examples

### Basic Recovery (just annotate your method)

```java
@AIRecover("Click the submit button and verify confirmation page")
public void submitForm() {
    click(By.id("submit"));  // If this fails, AI takes over
}
```

### With Parameter Substitution

```java
@AIRecover("Fill login form: enter username '{0}' and password '{1}', then click login")
public void login(String username, String password) {
    type(By.id("user"), username);
    type(By.id("pass"), password);
    click(By.id("login-btn"));
}
```

### With Data Extraction

```java
@AIRecover(value = "Ensure payment amount is visible",
           extract = {
               @AIExtract(key = "totalPayable", prompt = "the total payable amount, digits only"),
               @AIExtract(key = "currencyCode", prompt = "the currency code like USD or EUR")
           })
public void verifyPaymentAmount() {
    String amount = getText(By.className("total"));
    // If this fails, AI recovers AND extracts the values
}
```

### In Your Test Runner

```java
@BeforeSuite
public void setup() {
    AIRecoveryAgent.install();  // One line. That's it.
}
```

---

## Architecture

### Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Test Framework | Cucumber + TestNG | BDD test execution |
| Browser Automation | Selenium WebDriver | Primary test actions |
| AI Recovery | Playwright MCP | Connects to Chrome via CDP for recovery |
| AI Engine | Google Gemini 2.5 Flash | Analyzes page state, plans recovery actions |
| Bytecode Transform | ByteBuddy | Intercepts `@AIRecover` methods at runtime |
| Protocol | JSON-RPC 2.0 over stdio | Communication with Playwright MCP server |

### How ByteBuddy Transforms Your Code

```
Before AIRecoveryAgent.install():
┌─────────────────────────────┐
│ void fillCardDetails() {    │
│   sendKeys(name);  // FAIL  │ → Test crashes
│ }                           │
└─────────────────────────────┘

After AIRecoveryAgent.install():
┌─────────────────────────────┐
│ void fillCardDetails() {    │
│   try {                     │
│     sendKeys(name); // FAIL │ → Caught!
│   } catch (Exception e) {   │
│     AIRecoveryHandler       │
│       .attemptRecovery(...);│ → AI fixes it
│   }                         │
│ }                           │
└─────────────────────────────┘
```

### Recovery Flow Detail

```
AIRecoveryHandler.attemptRecovery()
    │
    ├── Build prompt from @AIRecover annotation + method parameters
    │
    ├── PlaywrightRecoveryOrchestrator.on(driver).intent(prompt).recover()
    │       │
    │       ├── Resolve CDP endpoint (from Chrome capabilities or config)
    │       │
    │       ├── PlaywrightMCPClient.connectToCDP(endpoint)
    │       │       │
    │       │       └── MCPJsonRpcClient (spawns `npx @playwright/mcp@latest`)
    │       │               │
    │       │               └── JSON-RPC 2.0 over stdin/stdout
    │       │
    │       └── OBSERVE-ACT LOOP:
    │           ├── snapshot() → Accessibility tree of current page
    │           ├── PlaywrightAIRecoveryClient.planNextActions()
    │           │       └── Gemini API call → returns tool calls
    │           ├── Execute actions (browser_click, browser_type, etc.)
    │           └── Loop until done or max steps
    │
    └── Save extracted data to DataStore (if @AIExtract present)
```

---

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `ENABLE_REMOTE_DEBUGGING` | `true` | Enable Chrome CDP for AI recovery |
| `ENABLE_REMOTE_DEBUGGING_PORT` | `9222` | Chrome remote debugging port |
| `AGENT_MODEL` | `gemini-2.5-flash` | Gemini model to use |
| `AGENT_MAX_RETRY` | `2` | Max recovery attempts per failure |
| `AGENT_MAX_STEPS` | `10` | Max observe-act loop iterations per attempt |
| `GEMINI_API_KEY` | - | Your Google Gemini API key |

---

## License

MIT
