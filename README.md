# Multi-Hostel Management System

Browser-based mini-project using Java 17, Spring Boot, HTML/CSS/JavaScript and PostgreSQL. It uses JDBC SQL against your existing `hostel` database and `aishwarya` schema, so it avoids changing the existing table design just to fit JPA entities.

## Existing database expected

- Database: `hostel`
- Schema: `aishwarya`
- PostgreSQL user: `aishwarya`
- Port: `5432`
- Tables: `hostel`, `warden`, `student`, `rooms`, `manage`, `enroll`, `fees`, `register`, `student_comments`
- View: `student_fee_view`

The code assumes the known columns and keys in the project prompt. Before running, verify the actual schema in pgAdmin. In particular, check data types for `hostel_id`, `room_id`, `transaction_id`, and `comment_id`; whether `hostel_id` has a sequence/default; and whether `student_comments.description` exists. This project has not been tested against your local database, so do not treat it as verified until you run the checks below.

## 1. Back up your database

In pgAdmin, right-click `hostel` → Backup. Keep the backup safe. This project does not drop/recreate the database or delete existing rows.

## 2. Inspect the schema

Run this query in pgAdmin Query Tool:

```sql
SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'aishwarya'
ORDER BY table_name, ordinal_position;
```

Run this to inspect primary and foreign keys:

```sql
SELECT tc.table_name, tc.constraint_type, tc.constraint_name,
       kcu.column_name, ccu.table_name AS referenced_table,
       ccu.column_name AS referenced_column
FROM information_schema.table_constraints tc
LEFT JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
 AND tc.table_schema = kcu.table_schema
LEFT JOIN information_schema.constraint_column_usage ccu
  ON ccu.constraint_name = tc.constraint_name
 AND ccu.table_schema = tc.table_schema
WHERE tc.table_schema = 'aishwarya'
ORDER BY tc.table_name, tc.constraint_type, tc.constraint_name;
```

## 3. Create the login table

Review and run `src/main/resources/db/migration/001_authentication.sql` in pgAdmin, connected to database `hostel`. It is additive and uses `CREATE TABLE IF NOT EXISTS`. If `aishwarya.app_users` already exists, inspect it first and compare columns/constraints; do not blindly change it.

The login table links roles to existing records:
- Admin: `role='ADMIN'`, `warden_name` and `student_id` are NULL.
- Warden: `role='WARDEN'`, `warden_name` must match an existing `aishwarya.warden.name`.
- Student: `role='STUDENT'`, `student_id` must match an existing student.

## 4. Configure the PostgreSQL password

Open `src/main/resources/application.properties`. `CHANGE_ME` is a placeholder. For a quick local run, replace it with the password for PostgreSQL user `aishwarya`. Do not commit real credentials to GitHub. Alternatively, set `DB_PASSWORD` as an environment variable.

## 5. Create the first Admin login

Set these environment variables in the terminal you will use to run the app. Choose your own values and never send the password in chat.

PowerShell example:

```powershell
$env:DB_PASSWORD="your_postgres_password"
$env:ADMIN_SETUP_USERNAME="admin"
$env:ADMIN_SETUP_PASSWORD="choose_a_strong_password"
```

The application creates that Admin only if the username is not already present. It stores a BCrypt hash. Unset the setup variables after the account is created. It does not reset existing passwords.

## 6. Import and run in Eclipse

1. Extract the ZIP.
2. In Eclipse choose **File → Import → Maven → Existing Maven Projects**.
3. Browse to the extracted `MultiHostelManagementSystem` directory and finish the import.
4. Right-click the project → **Maven → Update Project**.
5. Make sure the project uses JDK 17 or newer.
6. Run `MultiHostelManagementApplication.java` as **Java Application**.
7. Open `http://localhost:8080` in your browser.

If Maven is installed, you can also run `mvn spring-boot:run` from the project directory.

## 7. Add Warden and Student logins

Create accounts only after the linked warden/student already exists. Passwords must be BCrypt hashes, not plain text. To generate a hash, temporarily create/run a Java class in the project with:

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class HashPassword {
    public static void main(String[] args) {
        System.out.println(new BCryptPasswordEncoder().encode("choose-a-password"));
    }
}
```

Then insert the generated hash in pgAdmin. Example templates (replace placeholders):

```sql
INSERT INTO aishwarya.app_users(username, password_hash, role, warden_name, student_id)
VALUES ('warden_login', '<BCrypt hash>', 'WARDEN', '<exact existing warden name>', NULL);

INSERT INTO aishwarya.app_users(username, password_hash, role, warden_name, student_id)
VALUES ('student_login', '<BCrypt hash>', 'STUDENT', NULL, <existing student_id>);
```

## Role permissions

- **Admin:** add, view, edit and delete hostel and warden records only.
- **Warden:** manage students, rooms, allocations and fee records for their assigned hostel; update complaint status for students allocated to their hostel; view their assigned hostel.
- **Student:** view and edit their own profile, view their own allocation and fee records, and submit/view their own complaints. Students cannot manage hostel records or update complaint status.
- **Passed-out students:** the Warden's **Pass Out** action marks a student as `PASSED_OUT`, removes their active room allocation, updates room occupancy, and preserves fee/complaint history instead of hard-deleting the student record.

Student creation and room allocation are separate steps: after adding a student, allocate that student to a room. The student then appears in the Warden's active student list. Room occupancy is recalculated from active allocation records when allocations are added/removed.

## Included features

- Database-backed login/logout and BCrypt password verification.
- Backend role checks for the management operations above.
- Light responsive browser UI, search, validation, and readable error messages.
- Light responsive browser UI, search, validation, and readable error messages.
- Role checks enforced on backend API endpoints.

## Important limitations to verify

1. Hostel IDs and complaint IDs are entered manually in the current UI. If your database expects generated IDs, the insert logic/UI should be adapted to the actual default/sequence.
2. The existing `student_comments` table may or may not have `description`; the code supports listing with or without it and attempts the compatible insert.
3. Warden complaint access is inferred from student enrollment. If a student has no allocation, the current schema may not associate their complaint with a hostel.
4. Allocation increments/decrements `rooms.occupied`. Verify existing `occupied` values against existing `enroll` rows before using allocations; this app does not automatically rewrite old occupancy values.
5. Foreign keys can prevent deleting records that are still referenced. The app returns a conflict error rather than deleting linked records automatically.
6. Status values must match any check constraints in your existing database. If your schema permits only different values, update the options and validation after inspecting it.
7. The `register` table is preserved but not included as a main CRUD page in this simple version. Entry/exit management can be added if your team confirms it is required.

## Test before demonstration

- [ ] Application starts and connects to the correct database/schema.
- [ ] Admin login works.
- [ ] Dashboard counts reflect actual database rows.
- [ ] Admin can add/edit/delete hostels and wardens, but cannot manage student/room/allocation/fee/complaint records.
- [ ] Warden can add/edit/pass out students, manage rooms/allocations/fees, and update complaints for their assigned hostel.
- [ ] Composite room key `(hostel_id, room_id)` works.
- [ ] Allocation capacity checks work and occupancy stays consistent.
- [ ] Fee operations persist after page refresh.
- [ ] Student can only see their own information.
- [ ] Warden only sees records connected to their assigned hostel.
- [ ] Complaint submission/status update works with actual constraints.
- [ ] Logout works.
- [ ] Existing records remain intact.

This is a complete project starter implementation, but the local schema compatibility and end-to-end operation must be verified on your computer before calling it final.
