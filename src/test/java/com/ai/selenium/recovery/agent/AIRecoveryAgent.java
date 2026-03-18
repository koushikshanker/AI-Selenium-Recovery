package com.ai.selenium.recovery.agent;

import com.ai.selenium.recovery.annotations.AIRecover;
import com.ai.selenium.recovery.core.LogUtil;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;

/**
 * Installs the ByteBuddy agent that intercepts {@link AIRecover}-annotated methods.
 *
 * <p>Call {@link #install()} once before tests run (e.g., in {@code @BeforeSuite}).
 * After installation, any method annotated with {@code @AIRecover} will automatically
 * trigger Playwright MCP recovery on failure — no lambda wrappers or call-site changes needed.
 *
 * <p>What it does in one sentence:
 * <b>Find every method with @AIRecover and wrap it in a try-catch that triggers AI recovery on failure.</b>
 *
 * <pre>
 * Before install:
 * void fillCardDetails() {
 *     sendKeys(name);     // fails → test crashes
 * }
 *
 * After install:
 * void fillCardDetails() {
 *     try {
 *         sendKeys(name); // fails → caught
 *     } catch (Exception e) {
 *         AIRecoveryHandler.attemptRecovery(...); // AI fixes it
 *     }
 * }
 * </pre>
 */
public class AIRecoveryAgent {

    private static volatile boolean installed = false;

    private AIRecoveryAgent() {}

    /**
     * Install the ByteBuddy agent. Safe to call multiple times — only installs once.
     * Fails gracefully: if installation fails, tests run normally without AI recovery.
     */
    public static synchronized void install() {
        if (installed) return;

        try {
            Instrumentation instrumentation = ByteBuddyAgent.install();

            new AgentBuilder.Default()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .disableClassFormatChanges()
                    .type(ElementMatchers.declaresMethod(
                            ElementMatchers.isAnnotatedWith(AIRecover.class)))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                            builder.visit(
                                    Advice.to(AIRecoveryAdvice.class)
                                            .on(ElementMatchers.isAnnotatedWith(AIRecover.class))
                            )
                    )
                    .installOn(instrumentation);

            installed = true;
            LogUtil.info("[AIRecovery] ByteBuddy agent installed — @AIRecover interception active");
        } catch (Exception e) {
            LogUtil.error("[AIRecovery] Failed to install ByteBuddy agent: " + e.getMessage()
                    + ". Tests will run normally without AI recovery.");
        }
    }
}