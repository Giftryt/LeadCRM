package com.leadtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Sign-in is handled by AuthController + SessionAuthFilter, so Spring's default in-memory user is not needed.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class LeadTrackerApplication {

  public static void main(String[] args) {
    SpringApplication.run(LeadTrackerApplication.class, args);
  }
}
