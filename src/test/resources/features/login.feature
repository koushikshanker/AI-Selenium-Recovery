Feature: OrangeHRM Login
  Verify login functionality on OrangeHRM demo site.
  If any step encounters a Selenium error (element not found, stale element, etc.),
  the AI agent automatically takes over the browser and completes the action.

  @ai-recovery @login
  Scenario: Login with valid credentials
    Given User navigates to OrangeHRM login page "https://opensource-demo.orangehrmlive.com/web/index.php/auth/login"
    When User enters username "Admin"
    And User enters password "admin123"
    And User clicks on login button
    Then User should be redirected to the dashboard
    And User should see the dashboard title "Dashboard"
