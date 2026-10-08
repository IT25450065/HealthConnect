package com.healthconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * HealthConnect - Web-Based Medical Portal.
 *
 * SE2030 Software Engineering group project, SLIIT Year 2 Semester 1 2026.
 * Group 2026-Y2-S1-MLB-WEB3G1-07.
 *
 * Running main() starts an embedded Tomcat on http://localhost:8080 - there is
 * no separate web server to install. @SpringBootApplication switches on
 * component scanning of the com.healthconnect package, so every @Controller,
 * @Service, @Repository, @Configuration and @Component below this package is
 * found automatically.
 */
@SpringBootApplication
public class HealthConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthConnectApplication.class, args);
    }
}
