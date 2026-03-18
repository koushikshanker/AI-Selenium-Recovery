package com.ai.selenium.recovery.runner;

import com.ai.selenium.recovery.agent.AIRecoveryAgent;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;

@CucumberOptions(
        features = "src/test/resources/features",
        glue = "com.ai.selenium.recovery.steps",
        plugin = {
                "pretty",
                "html:target/cucumber-reports/cucumber.html",
                "json:target/cucumber-reports/Cucumber.json"
        },
        tags = "@ai-recovery"
)
public class TestRunner extends AbstractTestNGCucumberTests {

    /**
     * Install the AI Recovery agent before any tests run.
     * This wraps all @AIRecover-annotated methods with automatic try-catch recovery.
     */
    @BeforeSuite(alwaysRun = true)
    public void installAIRecoveryAgent() {
        AIRecoveryAgent.install();
    }

    @Override
    @DataProvider(parallel = false)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
