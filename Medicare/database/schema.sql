-- HealthConnect Hospital Portal: Pharmacist & Prescription Management Schema
-- Database: MySQL

CREATE DATABASE IF NOT EXISTS healthconnect_pharmacy;
USE healthconnect_pharmacy;

-- For a safe setup and import of inventory_data.json, run instead:
--   cd backend && npm run db:migrate
--
-- Do not add DROP TABLE statements here: the application migration preserves
-- existing MySQL records rather than deleting them during setup.

-- 1. Pharmacists Table
CREATE TABLE pharmacists (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  email VARCHAR(150) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 2. Medicines Table
CREATE TABLE medicines (
  id INT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) UNIQUE NOT NULL,
  name VARCHAR(150) NOT NULL,
  unit VARCHAR(30) NOT NULL,
  stock_qty INT NOT NULL DEFAULT 0,
  min_threshold INT NOT NULL DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 3. Stock Transactions Audit Log Table
CREATE TABLE stock_transactions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  medicine_id INT NOT NULL,
  qty_received INT NOT NULL,
  batch_ref VARCHAR(100),
  pharmacist_id INT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE,
  FOREIGN KEY (pharmacist_id) REFERENCES pharmacists(id) ON DELETE SET NULL
);

-- 4. Prescriptions Queue Table
CREATE TABLE prescriptions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_name VARCHAR(150) NOT NULL,
  doctor_name VARCHAR(150) NOT NULL,
  status ENUM('Pending', 'Dispensed') DEFAULT 'Pending',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 5. Prescription Line Items Table
CREATE TABLE prescription_items (
  id INT AUTO_INCREMENT PRIMARY KEY,
  prescription_id INT NOT NULL,
  medicine_id INT NOT NULL,
  dosage VARCHAR(100),
  frequency VARCHAR(100),
  duration VARCHAR(100),
  quantity INT NOT NULL DEFAULT 1,
  dispensed BOOLEAN DEFAULT FALSE,
  dispensed_at DATETIME NULL,
  dispensed_by INT NULL,
  FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE CASCADE,
  FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE,
  FOREIGN KEY (dispensed_by) REFERENCES pharmacists(id) ON DELETE SET NULL
);

-- ============================================================================
-- SEED DATA FOR DEMO & TESTING
-- ============================================================================

-- Seed Pharmacists (Password: password123 hashed using bcrypt)
INSERT INTO pharmacists (name, email, password_hash) VALUES
('Dr. Sarah Jenkins', 'pharmacist@healthconnect.com', '$2a$10$z4VxKAQWfMipAPz/fJf9kOzDdW82uDmjku6MSmQnyzn97Mq.r/x.a'),
('Alex Rivera, PharmD', 'alex.rivera@healthconnect.com', '$2a$10$z4VxKAQWfMipAPz/fJf9kOzDdW82uDmjku6MSmQnyzn97Mq.r/x.a');

-- Seed Medicines (Mix of normal, low stock, and zero stock)
INSERT INTO medicines (code, name, unit, stock_qty, min_threshold) VALUES
('MED-1001', 'Paracetamol 500mg', 'Tablets', 150, 50),
('MED-1002', 'Amoxicillin 250mg', 'Capsules', 12, 30),         -- LOW STOCK
('MED-1003', 'Ibuprofen 400mg', 'Tablets', 0, 40),              -- OUT OF STOCK
('MED-1004', 'Metformin 850mg', 'Tablets', 80, 25),
('MED-1005', 'Omeprazole 20mg', 'Capsules', 5, 20),             -- LOW STOCK
('MED-1006', 'Atorvastatin 10mg', 'Tablets', 0, 15),           -- OUT OF STOCK
('MED-1007', 'Azithromycin 500mg', 'Tablets', 65, 20),
('MED-1008', 'Ciprofloxacin 500mg', 'Tablets', 8, 15);          -- LOW STOCK

-- Seed Stock Transactions Audit History
INSERT INTO stock_transactions (medicine_id, qty_received, batch_ref, pharmacist_id, created_at) VALUES
(1, 100, 'BATCH-2026-001', 1, NOW() - INTERVAL 5 DAY),
(1, 50, 'BATCH-2026-015', 1, NOW() - INTERVAL 2 DAY),
(2, 20, 'BATCH-2026-009', 2, NOW() - INTERVAL 4 DAY),
(4, 80, 'BATCH-2026-003', 1, NOW() - INTERVAL 3 DAY),
(7, 65, 'BATCH-2026-012', 2, NOW() - INTERVAL 1 DAY);

-- Seed Sample Doctor Prescriptions (Pending & Dispensed)
-- Prescription #1: Pending (Patient: John Doe, Dr. Emily Vance)
INSERT INTO prescriptions (patient_name, doctor_name, status, created_at) VALUES
('John Doe', 'Dr. Emily Vance', 'Pending', NOW() - INTERVAL 3 HOUR);

INSERT INTO prescription_items (prescription_id, medicine_id, dosage, frequency, duration, quantity, dispensed) VALUES
(1, 1, '500mg', 'Twice daily', '5 days', 10, FALSE),           -- Paracetamol (In Stock: 150)
(1, 2, '250mg', 'Three times daily', '7 days', 21, FALSE);      -- Amoxicillin (Stock: 12 -> Insufficient for 21!)

-- Prescription #2: Pending (Patient: Maria Garcia, Dr. Robert Chen)
INSERT INTO prescriptions (patient_name, doctor_name, status, created_at) VALUES
('Maria Garcia', 'Dr. Robert Chen', 'Pending', NOW() - INTERVAL 1 HOUR);

INSERT INTO prescription_items (prescription_id, medicine_id, dosage, frequency, duration, quantity, dispensed) VALUES
(2, 4, '850mg', 'Once daily', '30 days', 30, FALSE),          -- Metformin (In Stock: 80)
(2, 5, '20mg', 'Once daily before breakfast', '14 days', 14, FALSE); -- Omeprazole (Stock: 5 -> Insufficient!)

-- Prescription #3: Pending (Patient: Michael Smith, Dr. Emily Vance)
INSERT INTO prescriptions (patient_name, doctor_name, status, created_at) VALUES
('Michael Smith', 'Dr. Emily Vance', 'Pending', NOW() - INTERVAL 30 MINUTE);

INSERT INTO prescription_items (prescription_id, medicine_id, dosage, frequency, duration, quantity, dispensed) VALUES
(3, 7, '500mg', 'Once daily', '3 days', 3, FALSE);            -- Azithromycin (In Stock: 65)

-- Prescription #4: Already Dispensed (Patient: Alice Johnson, Dr. Gregory House)
INSERT INTO prescriptions (patient_name, doctor_name, status, created_at) VALUES
('Alice Johnson', 'Dr. Gregory House', 'Dispensed', NOW() - INTERVAL 1 DAY);

INSERT INTO prescription_items (prescription_id, medicine_id, dosage, frequency, duration, quantity, dispensed, dispensed_at, dispensed_by) VALUES
(4, 1, '500mg', 'Every 6 hours as needed', '3 days', 12, TRUE, NOW() - INTERVAL 1 DAY, 1);
