package com.ai.selenium.recovery.steps;

import com.ai.selenium.recovery.agent.AIRecoveryAgent;
import com.ai.selenium.recovery.core.DataStore;
import com.ai.selenium.recovery.core.DriverManager;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;

/**
 * Cucumber hooks for WebDriver lifecycle management.
 */
public class Hooks {

    @BeforeAll
    public static void installAgent() {
        AIRecoveryAgent.install();
    }

    @Before
    public void setUp() {
        DriverManager.createDriver();
    }

    @After
    public void tearDown() {
        DriverManager.quitDriver();
        DataStore.clear();
    }
}
