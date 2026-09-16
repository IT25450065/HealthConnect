# HealthConnect - SQL Server & SSMS Setup Guide

**Project:** HealthConnect - Web-Based Medical Portal
**Module:** SE2030 Software Engineering, SLIIT Year 2 Semester 1 2026
**Group:** 2026-Y2-S1-MLB-WEB3G1-07

This guide assumes you have **never opened SQL Server Management Studio before**.
Follow it top to bottom and the Spring Boot application will connect at the end.
Every step says what you should see when it worked.

> Do this **before** trying to run the application. The app cannot start
> without a database to connect to.

---

## Contents

1. [Install SQL Server and SSMS, and connect](#1-install-sql-server-and-ssms-and-connect)
2. [Create the HealthConnect database](#2-create-the-healthconnect-database)
3. [Create the tables (full script)](#3-create-the-tables)
4. [Create a login for the application](#4-create-a-login-for-the-application)
5. [Enable TCP/IP and SQL Server Browser](#5-enable-tcpip-and-sql-server-browser)
6. [Connect Spring Boot to the database](#6-connect-spring-boot-to-the-database)
7. [Troubleshooting](#7-troubleshooting)
8. [Verify it works](#8-verify-it-works)

---

## 1. Install SQL Server and SSMS, and connect

You need **two separate downloads**. This surprises people: SQL Server is the
database engine (no windows, no buttons - it runs as a background service), and
SSMS is the program you actually look at.

### 1.1 Install the database engine

Go to Microsoft's SQL Server downloads page and pick one of the free editions:

| Edition | Pick it if |
|---|---|
| **Express** | You just want it to work. Free forever, 10 GB database limit - far more than this project needs. |
| **Developer** | You want the full feature set for learning. Free, but not licensed for production. |

Either is fine. Run the installer and choose **Basic** installation - accept
every default.

At the end the installer shows a summary. **Write down the "Instance Name" and
the "Connection String" it displays.** You will need the instance name in step 6.

- Express usually installs as a **named instance** called `SQLEXPRESS`, so your
  server is `localhost\SQLEXPRESS`.
- Developer usually installs as the **default instance**, so your server is
  just `localhost` and it listens on port **1433**.

### 1.2 Install SSMS

On that same summary screen there is an **"Install SSMS"** button, which opens
the SQL Server Management Studio download page. Download and install it
(it is a large download; accept the defaults).

### 1.3 Connect for the first time

Open **Microsoft SQL Server Management Studio**. A *Connect to Server* dialog
appears.

| Field | Value |
|---|---|
| Server type | `Database Engine` |
| **Server name** | `localhost\SQLEXPRESS` (Express) or `localhost` (Developer) |
| Authentication | `Windows Authentication` |
| Encryption *(SSMS 20+)* | `Optional`, **or** tick **Trust server certificate** |

Click **Connect**.

**It worked if:** a panel called *Object Explorer* appears on the left with your
server name at the top and folders underneath it (`Databases`, `Security`,
`Server Objects`, ...).

> **If it fails here**, jump to [section 7](#7-troubleshooting) - almost always
> the server name is wrong or the service is not running.

### 1.4 Turn on Mixed Mode authentication

We are going to create a SQL Server login for the application in step 4, and
SQL Server refuses those logins until Mixed Mode is switched on. Do it now:

1. In Object Explorer, **right-click the server name** (the very top line) ->
   **Properties**.
2. Select the **Security** page.
3. Under *Server authentication*, choose
   **SQL Server and Windows Authentication mode**.
4. Click **OK**. A message says the change takes effect after a restart.
5. Right-click the server name again -> **Restart** -> **Yes**.

---

## 2. Create the HealthConnect database

### Option A - through the GUI

1. In Object Explorer, right-click the **Databases** folder -> **New Database...**
2. In *Database name*, type exactly: `HealthConnect`
3. Leave everything else alone and click **OK**.

**It worked if:** `HealthConnect` now appears under *Databases*.

### Option B - with T-SQL (does the same thing)

Click **New Query** in the toolbar, paste this, and press **F5** (or click
**Execute**):

```sql
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'HealthConnect')
BEGIN
    CREATE DATABASE HealthConnect;
END
GO
```

**It worked if:** the Messages pane says `Commands completed successfully.`
(Right-click *Databases* -> **Refresh** to see it.)

---

## 3. Create the tables

Open a **New Query** window, and **first make sure it is pointed at the right
database** - this is the single most common mistake. Either pick
`HealthConnect` from the database dropdown in the toolbar, or just run the
script below, which starts with `USE HealthConnect;`.

Paste the whole script and press **F5**.

> **Should you even run this?** You have a choice:
>
> - **Run the script** (recommended for the report - it shows you designed the
>   schema) and then set `spring.jpa.hibernate.ddl-auto=none` in step 6.
> - **Skip the script** and let Hibernate build the tables from the `@Entity`
>   classes with `ddl-auto=update`. Faster, but then you have no schema script
>   to show.
>
> Either way the application works. Do **not** do both with `create` or
> `create-drop`, which wipes data on every start.

### 3.1 The full schema script

```sql
/* =====================================================================
   HealthConnect - schema creation script
   SE2030 Group Project, Group 2026-Y2-S1-MLB-WEB3G1-07
   Target: Microsoft SQL Server 2019 or later
   ===================================================================== */

USE HealthConnect;
GO

/* ---------------------------------------------------------------------
   Drop existing tables, children first, so the script can be re-run.
   --------------------------------------------------------------------- */
IF OBJECT_ID('dbo.feedback',           'U') IS NOT NULL DROP TABLE dbo.feedback;
IF OBJECT_ID('dbo.prescription_item',  'U') IS NOT NULL DROP TABLE dbo.prescription_item;
IF OBJECT_ID('dbo.prescription',       'U') IS NOT NULL DROP TABLE dbo.prescription;
IF OBJECT_ID('dbo.consultation',       'U') IS NOT NULL DROP TABLE dbo.consultation;
IF OBJECT_ID('dbo.video_session',      'U') IS NOT NULL DROP TABLE dbo.video_session;
IF OBJECT_ID('dbo.appointment',        'U') IS NOT NULL DROP TABLE dbo.appointment;
IF OBJECT_ID('dbo.medicine',           'U') IS NOT NULL DROP TABLE dbo.medicine;
IF OBJECT_ID('dbo.doctor',             'U') IS NOT NULL DROP TABLE dbo.doctor;
IF OBJECT_ID('dbo.patient',            'U') IS NOT NULL DROP TABLE dbo.patient;
IF OBJECT_ID('dbo.users',              'U') IS NOT NULL DROP TABLE dbo.users;
GO

/* ---------------------------------------------------------------------
   users - the "System User" abstraction from the use case diagram.
   Every actor who can log in has exactly one row here.
   Named "users" and not "user" because USER is a reserved word.
   --------------------------------------------------------------------- */
CREATE TABLE dbo.users (
    user_id         BIGINT IDENTITY(1,1) NOT NULL,
    full_name       NVARCHAR(100)        NOT NULL,
    email           NVARCHAR(120)        NOT NULL,
    password_hash   NVARCHAR(100)        NOT NULL,   -- BCrypt hash, never plain text
    role            NVARCHAR(30)         NOT NULL,
    phone           NVARCHAR(20)         NULL,
    active          BIT                  NOT NULL CONSTRAINT DF_users_active DEFAULT (1),
    created_at      DATETIME2            NOT NULL CONSTRAINT DF_users_created DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_users        PRIMARY KEY (user_id),
    CONSTRAINT UQ_users_email  UNIQUE (email),
    CONSTRAINT CK_users_role   CHECK (role IN
        ('PATIENT','DOCTOR','RECEPTIONIST','PHARMACIST','ADMIN','PATIENT_RELATIONS_OFFICER'))
);
GO

/* ---------------------------------------------------------------------
   patient - profile for a user whose role is PATIENT
   --------------------------------------------------------------------- */
CREATE TABLE dbo.patient (
    patient_id      BIGINT IDENTITY(1,1) NOT NULL,
    user_id         BIGINT               NOT NULL,
    date_of_birth   DATE                 NULL,
    gender          NVARCHAR(10)         NULL,
    address         NVARCHAR(255)        NULL,
    blood_group     NVARCHAR(5)          NULL,

    CONSTRAINT PK_patient         PRIMARY KEY (patient_id),
    CONSTRAINT UQ_patient_user    UNIQUE (user_id),
    CONSTRAINT FK_patient_user    FOREIGN KEY (user_id) REFERENCES dbo.users (user_id)
);
GO

/* ---------------------------------------------------------------------
   doctor - profile for a user whose role is DOCTOR.
   available_days is a comma-separated list of java.time.DayOfWeek names,
   e.g. 'MONDAY,TUESDAY,WEDNESDAY'.
   --------------------------------------------------------------------- */
CREATE TABLE dbo.doctor (
    doctor_id       BIGINT IDENTITY(1,1) NOT NULL,
    user_id         BIGINT               NOT NULL,
    specialization  NVARCHAR(80)         NOT NULL,
    qualification   NVARCHAR(120)        NULL,
    room_no         NVARCHAR(20)         NULL,
    available_days  NVARCHAR(120)        NOT NULL,
    available_from  TIME                 NOT NULL,
    available_to    TIME                 NOT NULL,

    CONSTRAINT PK_doctor        PRIMARY KEY (doctor_id),
    CONSTRAINT UQ_doctor_user   UNIQUE (user_id),
    CONSTRAINT FK_doctor_user   FOREIGN KEY (user_id) REFERENCES dbo.users (user_id),
    CONSTRAINT CK_doctor_hours  CHECK (available_from < available_to)
);
GO

/* ---------------------------------------------------------------------
   appointment - UC-01 (in person) and UC-04 (video) share this one table.
   --------------------------------------------------------------------- */
CREATE TABLE dbo.appointment (
    appointment_id        BIGINT IDENTITY(1,1) NOT NULL,
    patient_id            BIGINT               NOT NULL,
    doctor_id             BIGINT               NOT NULL,
    appointment_date_time DATETIME2            NOT NULL,
    appointment_type      NVARCHAR(20)         NOT NULL,
    status                NVARCHAR(20)         NOT NULL,
    reason                NVARCHAR(255)        NULL,
    created_at            DATETIME2            NOT NULL
        CONSTRAINT DF_appointment_created DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_appointment          PRIMARY KEY (appointment_id),
    CONSTRAINT FK_appointment_patient  FOREIGN KEY (patient_id) REFERENCES dbo.patient (patient_id),
    CONSTRAINT FK_appointment_doctor   FOREIGN KEY (doctor_id)  REFERENCES dbo.doctor  (doctor_id),
    CONSTRAINT CK_appointment_type     CHECK (appointment_type IN ('IN_PERSON','VIDEO')),
    CONSTRAINT CK_appointment_status   CHECK (status IN ('PENDING','CONFIRMED','COMPLETED','CANCELLED'))
);
GO

/* Speeds up the double-booking check in AppointmentService. */
CREATE INDEX IX_appointment_doctor_datetime
    ON dbo.appointment (doctor_id, appointment_date_time);
CREATE INDEX IX_appointment_patient_datetime
    ON dbo.appointment (patient_id, appointment_date_time);
GO

/* ---------------------------------------------------------------------
   video_session - UC-04. One optional online session per appointment.
   SCOPE NOTE: meeting_link is a placeholder URL. Live video calling
   (WebRTC) is out of scope for this project.
   --------------------------------------------------------------------- */
CREATE TABLE dbo.video_session (
    video_session_id BIGINT IDENTITY(1,1) NOT NULL,
    appointment_id   BIGINT               NOT NULL,
    meeting_link     NVARCHAR(255)        NOT NULL,
    status           NVARCHAR(20)         NOT NULL,
    platform_note    NVARCHAR(255)        NULL,
    created_at       DATETIME2            NOT NULL
        CONSTRAINT DF_video_created DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_video_session         PRIMARY KEY (video_session_id),
    CONSTRAINT UQ_video_appointment     UNIQUE (appointment_id),
    CONSTRAINT FK_video_appointment     FOREIGN KEY (appointment_id)
        REFERENCES dbo.appointment (appointment_id),
    CONSTRAINT CK_video_status          CHECK (status IN
        ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED'))
);
GO

/* ---------------------------------------------------------------------
   consultation - UC-02. Exactly one per appointment.
   Together, these rows ARE a patient's medical history - there is
   deliberately no separate PatientRecord table.
   --------------------------------------------------------------------- */
CREATE TABLE dbo.consultation (
    consultation_id        BIGINT IDENTITY(1,1) NOT NULL,
    appointment_id         BIGINT               NOT NULL,
    consultation_date_time DATETIME2            NOT NULL,
    symptoms               NVARCHAR(1000)       NULL,
    diagnosis              NVARCHAR(1000)       NULL,
    notes                  NVARCHAR(2000)       NULL,

    CONSTRAINT PK_consultation        PRIMARY KEY (consultation_id),
    CONSTRAINT UQ_consultation_appt   UNIQUE (appointment_id),
    CONSTRAINT FK_consultation_appt   FOREIGN KEY (appointment_id)
        REFERENCES dbo.appointment (appointment_id)
);
GO

/* ---------------------------------------------------------------------
   medicine - UC-03. The pharmacy inventory.
   --------------------------------------------------------------------- */
CREATE TABLE dbo.medicine (
    medicine_id       BIGINT IDENTITY(1,1) NOT NULL,
    name              NVARCHAR(120)        NOT NULL,
    strength          NVARCHAR(60)         NULL,
    unit              NVARCHAR(30)         NULL,
    stock_quantity    INT                  NOT NULL,
    reorder_threshold INT                  NOT NULL,

    CONSTRAINT PK_medicine           PRIMARY KEY (medicine_id),
    CONSTRAINT UQ_medicine_name      UNIQUE (name),
    CONSTRAINT CK_medicine_stock     CHECK (stock_quantity >= 0),
    CONSTRAINT CK_medicine_threshold CHECK (reorder_threshold >= 0)
);
GO

/* ---------------------------------------------------------------------
   prescription - created by the doctor in UC-02, consumed by the
   pharmacist in UC-03.
   --------------------------------------------------------------------- */
CREATE TABLE dbo.prescription (
    prescription_id      BIGINT IDENTITY(1,1) NOT NULL,
    consultation_id      BIGINT               NOT NULL,
    status               NVARCHAR(20)         NOT NULL,
    issued_at            DATETIME2            NOT NULL,
    dispensed_at         DATETIME2            NULL,
    dispensed_by_user_id BIGINT               NULL,
    pharmacist_note      NVARCHAR(500)        NULL,

    CONSTRAINT PK_prescription            PRIMARY KEY (prescription_id),
    CONSTRAINT FK_prescription_consult    FOREIGN KEY (consultation_id)
        REFERENCES dbo.consultation (consultation_id),
    CONSTRAINT FK_prescription_dispenser  FOREIGN KEY (dispensed_by_user_id)
        REFERENCES dbo.users (user_id),
    CONSTRAINT CK_prescription_status     CHECK (status IN ('PENDING','DISPENSED','CANCELLED'))
);
GO

/* ---------------------------------------------------------------------
   prescription_item - one medicine line on a prescription.
   The medicine_id link is what lets "dispense" know which stock to cut.
   --------------------------------------------------------------------- */
CREATE TABLE dbo.prescription_item (
    prescription_item_id BIGINT IDENTITY(1,1) NOT NULL,
    prescription_id      BIGINT               NOT NULL,
    medicine_id          BIGINT               NOT NULL,
    dosage               NVARCHAR(100)        NOT NULL,
    instructions         NVARCHAR(255)        NULL,
    quantity             INT                  NOT NULL,

    CONSTRAINT PK_prescription_item       PRIMARY KEY (prescription_item_id),
    CONSTRAINT FK_item_prescription       FOREIGN KEY (prescription_id)
        REFERENCES dbo.prescription (prescription_id),
    CONSTRAINT FK_item_medicine           FOREIGN KEY (medicine_id)
        REFERENCES dbo.medicine (medicine_id),
    CONSTRAINT CK_item_quantity           CHECK (quantity > 0)
);
GO

/* ---------------------------------------------------------------------
   feedback - UC-05. ONE table serving four screens: the patient submits,
   the doctor sees their ratings, the Patient Relations Officer responds,
   and the admin report aggregates. That shared data is the cross-module
   <<include>> from the use case diagram.
   --------------------------------------------------------------------- */
CREATE TABLE dbo.feedback (
    feedback_id          BIGINT IDENTITY(1,1) NOT NULL,
    patient_id           BIGINT               NOT NULL,
    doctor_id            BIGINT               NOT NULL,
    appointment_id       BIGINT               NOT NULL,
    rating               INT                  NOT NULL,
    comments             NVARCHAR(1000)       NULL,
    submitted_at         DATETIME2            NOT NULL,
    officer_response     NVARCHAR(1000)       NULL,
    responded_at         DATETIME2            NULL,
    responded_by_user_id BIGINT               NULL,

    CONSTRAINT PK_feedback            PRIMARY KEY (feedback_id),
    CONSTRAINT UQ_feedback_appt       UNIQUE (appointment_id),   -- one rating per visit
    CONSTRAINT FK_feedback_patient    FOREIGN KEY (patient_id)     REFERENCES dbo.patient (patient_id),
    CONSTRAINT FK_feedback_doctor     FOREIGN KEY (doctor_id)      REFERENCES dbo.doctor  (doctor_id),
    CONSTRAINT FK_feedback_appt       FOREIGN KEY (appointment_id) REFERENCES dbo.appointment (appointment_id),
    CONSTRAINT FK_feedback_responder  FOREIGN KEY (responded_by_user_id) REFERENCES dbo.users (user_id),
    CONSTRAINT CK_feedback_rating     CHECK (rating BETWEEN 1 AND 5)
);
GO

PRINT 'HealthConnect schema created successfully.';
GO
```

**It worked if:** the Messages pane ends with
`HealthConnect schema created successfully.` and, after right-clicking
*HealthConnect -> Tables -> Refresh*, you see all ten tables:
`appointment`, `consultation`, `doctor`, `feedback`, `medicine`, `patient`,
`prescription`, `prescription_item`, `users`, `video_session`.

### 3.2 The same thing through the table designer

If your lab sheet requires GUI screenshots, the equivalent manual steps for one
table are:

1. Expand **HealthConnect** -> right-click **Tables** -> **New** -> **Table...**
2. Type each *Column Name* and pick its *Data Type*; untick *Allow Nulls* where
   the script says `NOT NULL`.
3. For the id column, select the row, and in *Column Properties* below set
   **Identity Specification -> (Is Identity) = Yes**.
4. Right-click the id row -> **Set Primary Key** (a key icon appears).
5. For a foreign key: right-click in the designer -> **Relationships...** ->
   **Add** -> *Tables and Columns Specification* -> choose the primary-key table
   and column, and the foreign-key column.
6. For a check constraint: right-click -> **Check Constraints...** -> **Add** ->
   type the expression, e.g. `rating >= 1 AND rating <= 5`.
7. **Ctrl+S**, and name the table.

The script is faster and is reproducible, which is why it is the recommended
route.

---

## 4. Create a login for the application

**Do not let the application connect as `sa`.** `sa` can do anything on the
whole server; the app only needs to read and write the ten tables in one
database. This is the principle of least privilege, and markers look for it.

### 4.1 Which authentication mode should this project use?

| | SQL Server Authentication | Windows Authentication |
|---|---|---|
| How it identifies you | A username and password stored in SQL Server | Your logged-in Windows account |
| Password in `application.properties` | Yes | No |
| Works the same on all six members' laptops | **Yes** | No - each member's Windows account differs |
| Extra setup for the JDBC driver | None | Needs the native `mssql-jdbc_auth` DLL on the Java library path |

> **Recommendation for this project: SQL Server Authentication.**
> It is the only one of the two that lets all six group members share one
> `application.properties` and one set of instructions, and it avoids a native
> DLL that is easy to get wrong during a demo. Windows Authentication is more
> secure in a real corporate deployment because no password is stored in a
> file - mention that trade-off in the viva.

### 4.2 Create the login (SQL Server Authentication)

Open a **New Query** window and run:

```sql
USE master;
GO

-- The LOGIN is server-wide: it is how you get in the front door.
IF NOT EXISTS (SELECT name FROM sys.server_principals WHERE name = N'healthconnect_app')
BEGIN
    CREATE LOGIN healthconnect_app
        WITH PASSWORD    = 'HcApp#2026Sliit',   -- change this, and keep it somewhere safe
             CHECK_POLICY = ON;
END
GO

USE HealthConnect;
GO

-- The USER is database-specific: it is what that login may touch inside
-- the HealthConnect database.
IF NOT EXISTS (SELECT name FROM sys.database_principals WHERE name = N'healthconnect_app')
BEGIN
    CREATE USER healthconnect_app FOR LOGIN healthconnect_app;
END
GO

-- Read and write every table in this database, and nothing else.
ALTER ROLE db_datareader ADD MEMBER healthconnect_app;
ALTER ROLE db_datawriter ADD MEMBER healthconnect_app;
GO

PRINT 'Application login created.';
GO
```

> **If you chose to skip the schema script** and let Hibernate create the
> tables with `ddl-auto=update`, the login also needs permission to *create*
> tables. Add this one extra line:
>
> ```sql
> ALTER ROLE db_ddladmin ADD MEMBER healthconnect_app;
> GO
> ```
>
> If you ran the schema script in step 3, leave `db_ddladmin` out - the
> application never needs to change the schema.

**It worked if:** the Messages pane says `Application login created.`, and
under *Security -> Logins* (at the **server** level, not inside the database)
you can see `healthconnect_app` after a refresh.

### 4.3 If you want Windows Authentication instead

There is no login to create - your Windows account already has access as the
machine administrator who installed SQL Server. You only need to change the
JDBC URL, and install the native authentication DLL. See
[section 6.3](#63-windows-authentication-alternative).

---

## 5. Enable TCP/IP and SQL Server Browser

SSMS can talk to SQL Server over a local-only protocol called Shared Memory, so
**SSMS working does not mean the Java driver will work.** The JDBC driver needs
**TCP/IP**, which SQL Server Express ships with *disabled*. This is the single
most common reason a Spring Boot app cannot connect to a database that SSMS is
happily connected to.

### 5.1 Enable TCP/IP

1. Open **SQL Server Configuration Manager**.
   (Press Start and type `SQL Server Configuration Manager`. If Windows cannot
   find it, press **Win+R** and run `SQLServerManager16.msc` - try `15`, `14`
   or `13` instead of `16` for older versions.)
2. On the left, expand **SQL Server Network Configuration** ->
   **Protocols for MSSQLSERVER** (or **Protocols for SQLEXPRESS**).
3. Right-click **TCP/IP** -> **Enable** -> **OK** on the warning.

### 5.2 Pin the port to 1433

1. Still on **TCP/IP**, right-click -> **Properties** -> the **IP Addresses** tab.
2. Scroll all the way to the bottom, to the **IPAll** section.
3. Set **TCP Port** to `1433`.
4. **Clear the TCP Dynamic Ports box so it is completely empty.**
   (A named instance like SQLEXPRESS uses a random port by default, which is
   why a fixed JDBC URL cannot find it.)
5. Click **OK**.

### 5.3 Start the SQL Server Browser service

You only strictly need this if you connect by instance name
(`localhost\SQLEXPRESS`) rather than by port. Turning it on anyway costs
nothing:

1. In Configuration Manager, click **SQL Server Services** on the left.
2. Right-click **SQL Server Browser** -> **Properties** -> the **Service** tab ->
   set **Start Mode** to `Automatic` -> **OK**.
3. Right-click **SQL Server Browser** -> **Start**.

### 5.4 Restart the database engine

Any TCP/IP change needs a restart to take effect:

1. **SQL Server Services** -> right-click **SQL Server (MSSQLSERVER)** or
   **SQL Server (SQLEXPRESS)** -> **Restart**.

### 5.5 Confirm the port is listening

Open **Command Prompt** and run:

```
netstat -an | findstr 1433
```

**It worked if:** you see a line ending in `LISTENING`, such as
`TCP    0.0.0.0:1433    0.0.0.0:0    LISTENING`.

---

## 6. Connect Spring Boot to the database

### 6.1 The Maven dependency

This is already in `pom.xml`. You do not need to add it - this section is here
so you can point at it in the viva:

```xml
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <scope>runtime</scope>
</dependency>
```

There is no `<version>` tag because `spring-boot-starter-parent` manages the
version for us. If you ever copy this dependency into a project that does not
use the Spring Boot parent, you must add one, for example
`<version>12.8.1.jre11</version>`.

`<scope>runtime</scope>` means our own code never imports a driver class - only
the running JVM needs it.

### 6.2 SQL Server Authentication settings (recommended)

Open `src/main/resources/application.properties` and set these four lines:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=HealthConnect;encrypt=true;trustServerCertificate=true
spring.datasource.username=healthconnect_app
spring.datasource.password=HcApp#2026Sliit
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
```

Reading that URL piece by piece:

| Piece | What it means |
|---|---|
| `jdbc:sqlserver://` | Use the Microsoft SQL Server driver |
| `localhost:1433` | Server on this machine, port 1433 (from step 5.2) |
| `databaseName=HealthConnect` | Which database on that server |
| `encrypt=true` | Encrypt the connection. **Driver 10.2 and later default this to true**, so you cannot simply ignore it. |
| `trustServerCertificate=true` | Accept the self-signed certificate a local dev server generates. **Required for local development**, and the thing to remove for a real production server with a proper certificate. |

**Using a named instance instead of a port?** Use this form - and note the
**double backslash**, because `\` is an escape character in a `.properties`
file:

```properties
spring.datasource.url=jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=HealthConnect;encrypt=true;trustServerCertificate=true
```

### 6.3 Windows Authentication (alternative)

Delete the `username` and `password` lines entirely and use:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=HealthConnect;integratedSecurity=true;encrypt=true;trustServerCertificate=true
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
```

`integratedSecurity=true` needs a **native DLL** that does not come with the
Maven dependency:

1. Download the `mssql-jdbc` **package** (the `.zip`, not just the jar) from
   Microsoft, matching your driver version.
2. Inside it find `auth\x64\mssql-jdbc_auth-<version>.x64.dll`.
3. Either copy that DLL into a folder already on your `PATH`, or start the app
   with `-Djava.library.path="C:\path\to\the\folder"`.

If it is missing you get
`This driver is not configured for integrated authentication`. This extra step
on six different laptops is exactly why section 4.1 recommends SQL Server
Authentication for this project.

### 6.4 Which `ddl-auto` should you use?

`spring.jpa.hibernate.ddl-auto` tells Hibernate what to do to the schema at
startup:

| Value | What it does | Use it when |
|---|---|---|
| `none` | Nothing at all. | **You ran the schema script in step 3.** Safest. |
| `validate` | Compares every `@Entity` to the real tables and **refuses to start** if they disagree. Changes nothing. | You ran the script and want a typo in it caught immediately. |
| `update` | Creates missing tables and columns. Never drops anything. | You **skipped** the script and want Hibernate to build the schema. |
| `create` / `create-drop` | **Drops and recreates every table**, destroying all data. | Never, on this project. |

**The answer the brief asks for:** if you created the tables manually with the
script in step 3, set

```properties
spring.jpa.hibernate.ddl-auto=none
```

or, better for catching mistakes,

```properties
spring.jpa.hibernate.ddl-auto=validate
```

`validate` is the better of the two because it turns "I mistyped a column name
in the script" into a clear startup error naming the column, instead of a
confusing failure hours later when that screen is first opened. It is safe
precisely because it never modifies your schema.

The project ships with `ddl-auto=update` so that it runs even if you skip the
script. **Change it to `none` or `validate` once your tables exist.**

> One detail that makes `validate` work here: the script uses `NVARCHAR`
> columns, and Hibernate maps `String` to `VARCHAR` by default, which
> `validate` would report as a mismatch. `application.properties` therefore
> sets
> `spring.jpa.properties.hibernate.use_nationalized_character_data=true`,
> which tells Hibernate to expect `NVARCHAR`. If you ever rewrite the script
> with `VARCHAR`, remove that line.

### 6.5 Start the application

From the project folder:

```
mvn spring-boot:run
```

**It worked if** the console ends with something like:

```
Tomcat started on port 8080 (http)
[HealthConnect] Empty database detected - inserting demo data...
[HealthConnect] Demo data inserted. Every account's password is: health123
Started HealthConnectApplication in 6.4 seconds
```

Open <http://localhost:8080> and log in as `admin@healthconnect.lk` /
`health123`.

---

## 7. Troubleshooting

Work down this table. The error text in the first column is what appears in the
Spring Boot console.

| Error you see | What is actually wrong | Fix |
|---|---|---|
| `The TCP/IP connection to the host localhost, port 1433 has failed. Error: "Connection refused"` | TCP/IP is disabled, or the instance is on a random port | Do [step 5.1](#51-enable-tcpip) and [5.2](#52-pin-the-port-to-1433), then **restart the SQL Server service**. Confirm with `netstat -an | findstr 1433`. |
| Same error, but `netstat` **does** show 1433 listening | Windows Firewall is blocking the port | Windows Defender Firewall -> *Advanced settings* -> *Inbound Rules* -> *New Rule* -> **Port** -> TCP **1433** -> Allow. (A local connection is usually not blocked, so suspect this only after the two above.) |
| `The driver could not establish a secure connection to SQL Server by using Secure Sockets Layer (SSL) encryption` | Driver 10.2+ encrypts by default and your dev server has a self-signed certificate | Add **`;trustServerCertificate=true`** to the JDBC URL. |
| `Login failed for user 'healthconnect_app'` | Mixed Mode is off, the password is wrong, or the database user was never created | Redo [step 1.4](#14-turn-on-mixed-mode-authentication) **and restart the server**; then re-run the script in [step 4.2](#42-create-the-login-sql-server-authentication). Check the password has no stray space. |
| `Cannot open database "HealthConnect" requested by the login` | The LOGIN exists but the USER inside that database does not | Run the `CREATE USER ... FOR LOGIN ...` and both `ALTER ROLE` statements from [step 4.2](#42-create-the-login-sql-server-authentication). |
| `Cannot find driver class ... SQLServerDriver` / `Failed to determine a suitable driver class` | The driver is not on the classpath | Confirm the `mssql-jdbc` dependency is in `pom.xml`, then reload Maven (IntelliJ: the Maven panel's refresh button) or run `mvn clean install`. |
| `The server principal ... is not able to access the database` or `The instance of SQL Server you attempted to connect to does not support encryption` | Instance name in the URL is wrong | If you installed Express, the server is `localhost\\SQLEXPRESS`, not `localhost`. Remember the **double backslash** in a `.properties` file. Check the real name in SSMS with `SELECT @@SERVERNAME;`. |
| `SQLServerException: ... Error: "The connection string contains a badly formed name or value"` | A single `\` in `application.properties` was eaten as an escape | Double it: `localhost\\SQLEXPRESS`. |
| `Schema-validation: missing table [appointment]` | `ddl-auto=validate` but the tables were never created | Run the script in [step 3](#3-create-the-tables), or switch to `ddl-auto=update` for one run. |
| `Schema-validation: wrong column type ... found [varchar], but expecting [nvarchar]` | The `use_nationalized_character_data` line was removed but the script used `NVARCHAR` | Put the line back (see [6.4](#64-which-ddl-auto-should-you-use)), or rewrite the script with `VARCHAR`. |
| `CREATE TABLE permission denied in database 'HealthConnect'` | `ddl-auto=update` with a login that only has read/write | Either add `db_ddladmin` (see [4.2](#42-create-the-login-sql-server-authentication)) or run the schema script and switch to `ddl-auto=none`. |
| App starts, but no demo data and you cannot log in | The seeder only runs when `users` is **empty** | `SELECT COUNT(*) FROM users;` - if there are rows, the seeder was skipped by design. Use an account that exists, or clear the tables and restart. |
| `Port 8080 was already in use` | Something else is on 8080 | Add `server.port=8081` to `application.properties`. |

---

## 8. Verify it works

The point of this section is to prove the application really is writing to
**your** SQL Server, not to some in-memory database.

### 8.1 Count what the seeder inserted

With the application running, go back to SSMS, open a **New Query** on
`HealthConnect`, and run:

```sql
USE HealthConnect;
GO

SELECT 'users' AS table_name, COUNT(*) AS row_count FROM dbo.users
UNION ALL SELECT 'patient',           COUNT(*) FROM dbo.patient
UNION ALL SELECT 'doctor',            COUNT(*) FROM dbo.doctor
UNION ALL SELECT 'appointment',       COUNT(*) FROM dbo.appointment
UNION ALL SELECT 'consultation',      COUNT(*) FROM dbo.consultation
UNION ALL SELECT 'prescription',      COUNT(*) FROM dbo.prescription
UNION ALL SELECT 'prescription_item', COUNT(*) FROM dbo.prescription_item
UNION ALL SELECT 'medicine',          COUNT(*) FROM dbo.medicine
UNION ALL SELECT 'video_session',     COUNT(*) FROM dbo.video_session
UNION ALL SELECT 'feedback',          COUNT(*) FROM dbo.feedback;
```

**It worked if:** `users` is 10, `medicine` is 8, `appointment` is 5, and
nothing is 0.

### 8.2 Register a test user and watch the row appear

1. In the browser, go to <http://localhost:8080/register>.
2. Register a patient with the email `test@demo.lk` and any password.
3. Back in SSMS, run:

```sql
SELECT user_id, full_name, email, role, active, created_at
FROM dbo.users
WHERE email = 'test@demo.lk';
```

**It worked if:** exactly one row comes back, created seconds ago.

**Now look at the password column** - this is the part worth showing in the demo:

```sql
SELECT email, password_hash FROM dbo.users WHERE email = 'test@demo.lk';
```

You will see something like
`$2a$10$N9qo8uLOickgx2ZMRZoMye1VdL7X6hI3uKJpP2Xo3m6nCGZxBnVXK` - a BCrypt hash,
not the password you typed. The `$2a$` prefix identifies BCrypt and `$10$` is
the cost factor. Even someone who steals this table cannot read the passwords.

### 8.3 Watch a booking land in the database

1. Log in as `amal@example.com` / `health123`.
2. Book any appointment.
3. In SSMS:

```sql
SELECT TOP 5
       a.appointment_id,
       pu.full_name AS patient,
       du.full_name AS doctor,
       a.appointment_date_time,
       a.appointment_type,
       a.status
FROM dbo.appointment a
JOIN dbo.patient p ON p.patient_id = a.patient_id
JOIN dbo.users  pu ON pu.user_id   = p.user_id
JOIN dbo.doctor  d ON d.doctor_id  = a.doctor_id
JOIN dbo.users  du ON du.user_id   = d.user_id
ORDER BY a.appointment_id DESC;
```

**It worked if:** your new booking is the top row with status `PENDING`.

### 8.4 Watch the pharmacy stock move

This proves the `<<include>>` "Mark Prescription as Dispensed **includes**
Update Medicine Stock" is real and not just drawn on a diagram.

1. Note the stock before:

```sql
SELECT name, stock_quantity FROM dbo.medicine WHERE name = 'Paracetamol';
```

2. Log in as `pharmacy@healthconnect.lk` / `health123`, open the pending
   prescription for Nimali Jayawardena, and press **Mark as dispensed and
   update stock**.
3. Run the same `SELECT` again.

**It worked if:** `stock_quantity` has dropped by exactly the quantity on the
prescription line, and:

```sql
SELECT prescription_id, status, dispensed_at, dispensed_by_user_id
FROM dbo.prescription
ORDER BY prescription_id DESC;
```

now shows that prescription as `DISPENSED` with a timestamp.

### 8.5 Starting over with a clean database

If you want to re-run the demo from scratch, delete the rows child-first (the
foreign keys will not let you do it in any other order), then restart the
application and the seeder will refill everything:

```sql
USE HealthConnect;
GO
DELETE FROM dbo.feedback;
DELETE FROM dbo.prescription_item;
DELETE FROM dbo.prescription;
DELETE FROM dbo.consultation;
DELETE FROM dbo.video_session;
DELETE FROM dbo.appointment;
DELETE FROM dbo.medicine;
DELETE FROM dbo.doctor;
DELETE FROM dbo.patient;
DELETE FROM dbo.users;
GO
```

Or re-run the whole schema script from [step 3](#3-create-the-tables), which
drops and recreates every table.

---

*End of guide. If the application starts, the login page loads, and section 8.2
shows a BCrypt hash in the `users` table, your database setup is complete.*
