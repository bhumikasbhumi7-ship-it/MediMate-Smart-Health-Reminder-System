# MediMate – Smart Health Reminder System (Java)

A Java Swing desktop application for organising medicine reminders.

## Features
- User registration and login.
- Passwords are stored as salted PBKDF2 hashes (demo implementation).
- Add medicine name, dosage and 24-hour reminder time.
- View reminders and today's status.
- Mark a medicine as taken.
- View today's taken-medicine history.
- Delete reminders.
- Pop-up reminders while the application is running.
- Separate medicine records for each username.

## Requirements
- JDK 8 or later
- VS Code (optional) or any Java IDE
- No external libraries

## Run
Open a terminal in this folder and run:

```bash
javac MediMate.java
java MediMate
```

On first launch, choose **Create account**, enter a username and password, then log in.

## Data files
The app creates `medimate_users.txt` and `medimate_medicines.txt` in the current working directory. Keep these files private. They are local demo storage, not a production database.

## Important limitations
- The app must remain open for reminder pop-ups to appear.
- This is an educational prototype, not a medical device or clinical decision-support system.
- It does not diagnose conditions or recommend medicines/dosages. Enter only clinician-prescribed dosage instructions.
- For a real deployment, use a secure database, stronger account/session controls, encrypted transport, backups, privacy review and proper reminder scheduling.
