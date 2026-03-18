package com.ai.selenium.recovery.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a data value to extract from the page after AI recovery succeeds.
 * Used within {@link AIRecover#extract()}.
 *
 * <p>After AI recovery fixes the page, a fresh snapshot is taken and the AI
 * extracts the requested value. The result is saved to
 * {@code DataStore.saveData(key, extractedValue)}.
 *
 * <pre>
 * &#64;AIRecover(value = "Ensure payment amount is visible",
 *           extract = {
 *               &#64;AIExtract(key = "totalPayable", prompt = "the total payable amount, digits only"),
 *               &#64;AIExtract(key = "currencyCode", prompt = "the currency code like USD or EUR")
 *           })
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface AIExtract {

    /** The key to save the extracted value under in DataStore. */
    String key();

    /** Description of what to extract from the page. Be specific. */
    String prompt();
}