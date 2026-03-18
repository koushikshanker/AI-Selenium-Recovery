package com.ai.selenium.recovery.pages;

import com.ai.selenium.recovery.core.DriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Base page object providing common Selenium utilities.
 */
public abstract class BasePage {

    protected WebDriver getDriver() {
        return DriverManager.getDriver();
    }

    protected WebElement waitForElement(By locator, int timeoutSeconds) {
        return new WebDriverWait(getDriver(), Duration.ofSeconds(timeoutSeconds))
                .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected void click(By locator) {
        waitForElement(locator, 10).click();
    }

    protected void type(By locator, String text) {
        WebElement element = waitForElement(locator, 10);
        element.clear();
        element.sendKeys(text);
    }

    protected String getText(By locator) {
        return waitForElement(locator, 10).getText();
    }

    protected void navigateTo(String url) {
        getDriver().get(url);
    }
}
