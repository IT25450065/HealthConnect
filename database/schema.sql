-- ============================================================================
-- HealthConnect Unified Database Schema (Team Shared Database Master)
-- ============================================================================

CREATE DATABASE IF NOT EXISTS healthconnect;
USE healthconnect;

-- ============================================================================
-- 1. PHARMACY MODULE TABLES (Ahamed's Module)
-- ============================================================================

-- Pharmacists / Staff Accounts
CREATE TABLE IF NOT EXISTS pharmacists (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(120) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Medicines Inventory Master Table (PB06)
CREATE TABLE IF NOT EXISTS medicines (
  id INT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  unit VARCHAR(50) NOT NULL,
  min_threshold INT NOT NULL DEFAULT 10,
  stock_qty INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Stock Addition Delivery Audit Trail (PB06)
CREATE TABLE IF NOT EXISTS stock_transactions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  medicine_id INT NOT NULL,
  qty_received INT NOT NULL,
  batch_ref VARCHAR(100) NOT NULL,
  pharmacist_id INT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE,
  FOREIGN KEY (pharmacist_id) REFERENCES pharmacists(id) ON DELETE SET NULL
);

-- Doctor Prescriptions Header Table (PB13)
CREATE TABLE IF NOT EXISTS prescriptions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_name VARCHAR(120) NOT NULL,
  doctor_name VARCHAR(120) NOT NULL,
  status ENUM('Pending', 'Dispensed', 'Cancelled') DEFAULT 'Pending',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Doctor Prescriptions Line Items (PB13)
CREATE TABLE IF NOT EXISTS prescription_items (
  id INT AUTO_INCREMENT PRIMARY KEY,
  prescription_id INT NOT NULL,
  medicine_id INT NOT NULL,
  dosage VARCHAR(50) NOT NULL,
  frequency VARCHAR(50) NOT NULL,
  duration VARCHAR(50) NOT NULL,
  quantity INT NOT NULL,
  dispensed BOOLEAN DEFAULT FALSE,
  dispensed_at TIMESTAMP NULL,
  FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE CASCADE,
  FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE
);

-- ============================================================================
-- 2. TEAM MODULE TABLE PLACEHOLDERS
-- ============================================================================

-- Appointments Module (Rathnayaka's Module)
-- CREATE TABLE IF NOT EXISTS appointments (...);

-- Consultations Module (Gunathilake's Module)
-- CREATE TABLE IF NOT EXISTS consultations (...);

-- Telemedicine Module (Adithya's Module)
-- CREATE TABLE IF NOT EXISTS telemedicine_sessions (...);

-- Feedback Module (Perera's Module)
-- CREATE TABLE IF NOT EXISTS feedbacks (...);

-- Admin Module (Vishalan's Module)
-- CREATE TABLE IF NOT EXISTS admin_logs (...);
