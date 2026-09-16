# HealthConnect - what is in this folder

SE2030 Software Engineering group project | Group 2026-Y2-S1-MLB-WEB3G1-07

Three deliverables:

| # | File / folder | What it is |
|---|---|---|
| 1 | **`healthconnect/`** | The complete, runnable Spring Boot Maven project. Open `healthconnect/README.md` first - it lists the versions, the module-to-member mapping, how to run it, the demo logins, and every design assumption made. |
| 2 | **`DBMS-Setup-Guide.md`** | SQL Server + SSMS from a blank machine: install, create the database, the full `CREATE TABLE` script, the application login, TCP/IP setup, the exact JDBC settings, a troubleshooting table, and how to verify writes are landing. |
| 3 | **`Java-Code-Walkthrough.md`** | The teaching document for explaining the codebase to the other five members. Uses the appointment booking flow as the running example, and ends with a per-member section mapping that example onto each person's own module. |

## The 60-second version

1. Read `DBMS-Setup-Guide.md` sections 1-5 and set up SQL Server.
2. Put your SQL login and password into
   `healthconnect/src/main/resources/application.properties`.
3. `cd healthconnect && mvn spring-boot:run`
4. Open <http://localhost:8080> and log in as `admin@healthconnect.lk`
   with the password `health123`.

Demo data is inserted automatically the first time the app starts against an
empty database.

## Before the viva

Everyone should read `Java-Code-Walkthrough.md` sections 1-8 once, then their
own row in section 9. Section 10 is a list of likely questions with answers.
