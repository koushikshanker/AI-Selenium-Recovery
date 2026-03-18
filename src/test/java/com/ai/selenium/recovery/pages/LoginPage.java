package com.ai.selenium.recovery.pages;

import com.ai.selenium.recovery.annotations.AIRecover;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class LoginPage extends BasePage {

    // Locators from Playwright MCP DOM inspection - OrangeHRM login page
    private final By usernameField = By.xpath("//input[@name='usernam']");
    private final By passwordField = By.xpath("//input[@name='passwor']");
    private final By loginButton = By.xpath("//button[@type='submit']");
    private final By loginTitle = By.xpath("//h5[contains(@class,'orangehrm-login-title')]");
    private final By forgotPasswordLink = By.xpath("//p[contains(@class,'orangehrm-login-forgot-header')]");

    @AIRecover("Navigate to OrangeHRM login page")
    public void openLoginPage(String url) {
        navigateTo(url);
    }

    @AIRecover("Enter username '{0}' in the username field")
    public void enterUsername(String username) {
        WebElement element = waitForElement(usernameField, 10);
        element.clear();
        element.sendKeys(username);
    }

    @AIRecover("Enter password '{0}' in the password field")
    public void enterPassword(String password) {
        type(passwordField, password);
    }

    @AIRecover("Click the login button")
    public void clickOnLoginButton() {
        click(loginButton);
    }

    @AIRecover("Fill login form: 1. Enter username '{0}' in the username field, "
            + "2. Enter password '{1}' in the password field, "
            + "3. Click the login/submit button")
    public void login(String username, String password) {
        type(usernameField, username);
        type(passwordField, password);
        click(loginButton);
    }

    @AIRecover("Verify the login page title is displayed")
    public String getLoginTitle() {
        return getText(loginTitle);
    }
}