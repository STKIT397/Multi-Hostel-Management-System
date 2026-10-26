-- Run once in database hostel, schema aishwarya, only if student.status does not exist.
ALTER TABLE aishwarya.student
ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
