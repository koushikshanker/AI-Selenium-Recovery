package com.ai.selenium.recovery.pages;

import com.ai.selenium.recovery.annotations.AIRecover;
import org.openqa.selenium.By;

public class DashboardPage extends BasePage {

    // Locators from Playwright MCP DOM inspection - OrangeHRM dashboard page
    private final By dashboardTitle = By.xpath("//h6[text()='Dashboard']");
    private final By userDropdown = By.xpath("//li[@class='oxd-userdropdown']");
    private final By sidebarSearchBox = By.xpath("//input[@placeholder='Search']");

    @AIRecover("Verify the dashboard page is displayed after login")
    public boolean isDashboardDisplayed() {
        return waitForElement(dashboardTitle, 10).isDisplayed();
    }

    @AIRecover("Get the dashboard page title text")
    public String getDashboardTitle() {
        return getText(dashboardTitle);
    }
}