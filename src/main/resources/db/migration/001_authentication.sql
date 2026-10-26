-- Additive migration only. Run against database hostel. Does not delete existing data.
CREATE TABLE IF NOT EXISTS aishwarya.app_users (
 user_id BIGSERIAL PRIMARY KEY,
 username VARCHAR(80) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(10) NOT NULL CHECK (role IN ('ADMIN','WARDEN','STUDENT')),
 warden_name VARCHAR(100), student_id INTEGER,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT app_users_warden_fk FOREIGN KEY (warden_name) REFERENCES aishwarya.warden(name) ON UPDATE CASCADE ON DELETE SET NULL,
 CONSTRAINT app_users_student_fk FOREIGN KEY (student_id) REFERENCES aishwarya.student(student_id) ON UPDATE CASCADE ON DELETE SET NULL,
 CONSTRAINT app_users_role_link_check CHECK ((role='ADMIN' AND warden_name IS NULL AND student_id IS NULL) OR (role='WARDEN' AND warden_name IS NOT NULL AND student_id IS NULL) OR (role='STUDENT' AND student_id IS NOT NULL AND warden_name IS NULL))
);
