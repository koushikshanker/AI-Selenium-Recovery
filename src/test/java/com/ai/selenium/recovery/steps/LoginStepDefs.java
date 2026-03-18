package com.ai.selenium.recovery.steps;

import com.ai.selenium.recovery.pages.DashboardPage;
import com.ai.selenium.recovery.pages.LoginPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.Assert;

public class LoginStepDefs {

    private final LoginPage loginPage = new LoginPage();
    private final DashboardPage dashboardPage = new DashboardPage();

    @Given("User navigates to OrangeHRM login page {string}")
    public void userNavigatesToOrangeHRMLoginPage(String url) {
        loginPage.openLoginPage(url);
    }

    @When("User enters username {string}")
    public void userEntersUsername(String username) {
        loginPage.enterUsername(username);
    }

    @And("User enters password {string}")
    public void userEntersPassword(String password) {
        loginPage.enterPassword(password);
    }

    @And("User clicks on login button")
    public void userClicksOnLoginButton() {
        loginPage.clickOnLoginButton();
    }

    @Then("User should be redirected to the dashboard")
    public void userShouldBeRedirectedToTheDashboard() {
        Assert.assertTrue(dashboardPage.isDashboardDisplayed(), "Dashboard should be displayed after login");
    }

    @Then("User should see the dashboard title {string}")
    public void userShouldSeeTheDashboardTitle(String expectedTitle) {
        String actualTitle = dashboardPage.getDashboardTitle();
        Assert.assertEquals(actualTitle, expectedTitle, "Dashboard title should match");
    }
}