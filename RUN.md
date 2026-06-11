# SWMS - How to Run the Project

This project is built with Spring Boot (Java) and connects to a Supabase PostgreSQL database. Follow these steps to run it locally.

---

## 1. Environment Configuration

Before running any Maven or Java commands, you must configure your path variables using the pre-configured local environment script:

```bash
source env.sh
```

---

## 2. Compile the Project

To clean and compile the Java source files:

```bash
mvn clean compile
```

---

## 3. Run the Application

### Option A: Run in Foreground
To start the application normally in the terminal:

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv6Addresses=true"
```

### Option B: Run in Background
To run the server in the background so you can close your terminal window:

```bash
nohup mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv6Addresses=true" > server.log 2>&1 &
```
*The logs will be written to `server.log` in the root directory.*

---

## 4. Stop the Application

To find and stop the running application on port `8080`:

```bash
lsof -t -i :8080 | xargs kill -9
```

---

## 5. Portals & Default Credentials

Once started, open your web browser and navigate to:
* **Public Dashboard Portal**: `http://localhost:8080/dashboard`
* **Warden Login Portal**: `http://localhost:8080/warden/login.html`
* **Administrator Portal**: `http://localhost:8080/admin/login.html`

### Seed Credentials
* **Super Admin**: `admin` / `admin123`
* **District Admin**: `district_admin` / `district123`
* **Hostel Warden 1**: `warden_netaji` / `warden123`
* **Hostel Warden 2**: `warden_tagore` / `warden123`
