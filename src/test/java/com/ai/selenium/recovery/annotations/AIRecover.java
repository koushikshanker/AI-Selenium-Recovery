package com.ai.selenium.recovery.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for automatic AI-powered recovery on failure.
 * When the method throws, ByteBuddy Advice triggers Playwright MCP recovery
 * using the prompt from this annotation.
 *
 * <p>Usage — just annotate your existing Selenium methods:
 * <pre>
 * &#64;AIRecover("Fill credit card payment form")
 * private void fillCardDetails(String name, String card, String expiry, String cvv) {
 *     // normal Selenium code — no lambda wrapper needed
 * }
 * </pre>
 *
 * <p>For methods that need to extract data after recovery:
 * <pre>
 * &#64;AIRecover(value = "Ensure payment amount is visible",
 *           extract = {
 *               &#64;AIExtract(key = "totalPayable", prompt = "the total payable amount, digits only"),
 *               &#64;AIExtract(key = "currencyCode", prompt = "the currency code like USD or EUR")
 *           })
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AIRecover {

    /**
     * Intent of the step — what the Selenium code is trying to do.
     * The AI uses this (plus auto-appended parameter values) to determine recovery actions.
     */
    String value();

    /**
     * Data values to extract from the page after successful AI recovery.
     * Each {@link AIExtract} specifies a key and a prompt describing what to extract.
     */
    AIExtract[] extract() default {};
}