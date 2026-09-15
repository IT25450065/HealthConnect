/*
 * Creates the HealthConnect MySQL schema and imports the legacy JSON database.
 * It is deliberately idempotent: existing populated tables are never replaced.
 */
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });
require('dotenv').config({ path: path.join(__dirname, '..', '.env.local'), override: true });

const database = process.env.DB_NAME || 'healthconnect_pharmacy';
const legacyFile = path.join(__dirname, '..', '..', 'database', 'inventory_data.json');
const connectionOptions = {
  host: process.env.DB_HOST || '127.0.0.1',
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER || 'root',
  // Do not let an accidental trailing space in .env prevent authentication.
  password: (process.env.DB_PASSWORD || '').trim()
};

const tables = [
  `CREATE TABLE IF NOT EXISTS pharmacists (
    id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
  ) ENGINE=InnoDB`,
  `CREATE TABLE IF NOT EXISTS medicines (
    id INT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL, unit VARCHAR(30) NOT NULL,
    stock_qty INT NOT NULL DEFAULT 0, min_threshold INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_medicine_stock CHECK (stock_qty >= 0),
    CONSTRAINT chk_medicine_threshold CHECK (min_threshold >= 0)
  ) ENGINE=InnoDB`,
  `CREATE TABLE IF NOT EXISTS prescriptions (
    id INT AUTO_INCREMENT PRIMARY KEY, patient_name VARCHAR(150) NOT NULL,
    doctor_name VARCHAR(150) NOT NULL,
    status ENUM('Pending', 'Dispensed') NOT NULL DEFAULT 'Pending',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
  ) ENGINE=InnoDB`,
  `CREATE TABLE IF NOT EXISTS stock_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY, medicine_id INT NOT NULL,
    qty_received INT NOT NULL, batch_ref VARCHAR(100) NULL, pharmacist_id INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_received_quantity CHECK (qty_received > 0),
    CONSTRAINT fk_transaction_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE,
    CONSTRAINT fk_transaction_pharmacist FOREIGN KEY (pharmacist_id) REFERENCES pharmacists(id) ON DELETE SET NULL
  ) ENGINE=InnoDB`,
  `CREATE TABLE IF NOT EXISTS prescription_items (
    id INT AUTO_INCREMENT PRIMARY KEY, prescription_id INT NOT NULL, medicine_id INT NOT NULL,
    dosage VARCHAR(100) NULL, frequency VARCHAR(100) NULL, duration VARCHAR(100) NULL,
    quantity INT NOT NULL DEFAULT 1, dispensed BOOLEAN NOT NULL DEFAULT FALSE,
    dispensed_at DATETIME NULL, dispensed_by INT NULL,
    CONSTRAINT chk_prescription_quantity CHECK (quantity > 0),
    CONSTRAINT fk_item_prescription FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE CASCADE,
    CONSTRAINT fk_item_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE RESTRICT,
    CONSTRAINT fk_item_pharmacist FOREIGN KEY (dispensed_by) REFERENCES pharmacists(id) ON DELETE SET NULL
  ) ENGINE=InnoDB`
];

function asMysqlDate(value) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error(`Invalid timestamp in legacy data: ${value}`);
  return date.toISOString().slice(0, 19).replace('T', ' ');
}

async function main() {
  const admin = await mysql.createConnection(connectionOptions);
  await admin.query(`CREATE DATABASE IF NOT EXISTS \`${database.replace(/`/g, '``')}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
  await admin.end();

  const db = await mysql.createConnection({ ...connectionOptions, database });
  try {
    for (const statement of tables) await db.query(statement);
    const [[{ count }]] = await db.query('SELECT COUNT(*) AS count FROM pharmacists');
    if (count > 0) {
      console.log('Schema is ready. Existing MySQL data was left unchanged.');
      return;
    }
    if (!fs.existsSync(legacyFile)) {
      console.log('Schema is ready. No legacy JSON file was found, so nothing was imported.');
      return;
    }

    const legacy = JSON.parse(fs.readFileSync(legacyFile, 'utf8'));
    await db.beginTransaction();
    for (const p of legacy.pharmacists || []) {
      await db.query('INSERT INTO pharmacists (id, name, email, password_hash, created_at) VALUES (?, ?, ?, ?, ?)', [p.id, p.name, p.email, p.password_hash, asMysqlDate(p.created_at)]);
    }
    for (const m of legacy.medicines || []) {
      await db.query('INSERT INTO medicines (id, code, name, unit, stock_qty, min_threshold, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)', [m.id, m.code, m.name, m.unit, m.stock_qty, m.min_threshold, asMysqlDate(m.created_at)]);
    }
    for (const p of legacy.prescriptions || []) {
      await db.query('INSERT INTO prescriptions (id, patient_name, doctor_name, status, created_at) VALUES (?, ?, ?, ?, ?)', [p.id, p.patient_name, p.doctor_name, p.status, asMysqlDate(p.created_at)]);
    }
    for (const t of legacy.stock_transactions || []) {
      await db.query('INSERT INTO stock_transactions (id, medicine_id, qty_received, batch_ref, pharmacist_id, created_at) VALUES (?, ?, ?, ?, ?, ?)', [t.id, t.medicine_id, t.qty_received, t.batch_ref, t.pharmacist_id, asMysqlDate(t.created_at)]);
    }
    for (const i of legacy.prescription_items || []) {
      await db.query('INSERT INTO prescription_items (id, prescription_id, medicine_id, dosage, frequency, duration, quantity, dispensed, dispensed_at, dispensed_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)', [i.id, i.prescription_id, i.medicine_id, i.dosage, i.frequency, i.duration, i.quantity, Boolean(i.dispensed), asMysqlDate(i.dispensed_at), i.dispensed_by]);
    }
    await db.commit();
    console.log('Schema is ready and legacy JSON data was imported successfully.');
  } catch (error) {
    await db.rollback();
    throw error;
  } finally {
    await db.end();
  }
}

main().catch(error => {
  console.error(`MySQL migration failed: ${error.message}`);
  process.exitCode = 1;
});
