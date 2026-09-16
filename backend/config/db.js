const mysql = require('mysql2/promise');
const path = require('path');
const fs = require('fs');
const bcrypt = require('bcryptjs');
require('dotenv').config();
require('dotenv').config({ path: path.join(__dirname, '..', '.env.local'), override: true });

const dbType = (process.env.DB_TYPE || 'mysql').toLowerCase();
let pool = null;
let activeDriver = 'mysql';

const storageFilePath = path.join(__dirname, '..', '..', 'database', 'inventory_data.json');

// In-Memory / File-Persisted storage state
let memoryDb = {
  pharmacists: [],
  medicines: [],
  stock_transactions: [],
  prescriptions: [],
  prescription_items: []
};

async function initDb() {
  if (dbType === 'mysql') {
    try {
      pool = mysql.createPool({
        host: process.env.DB_HOST || 'localhost',
        port: parseInt(process.env.DB_PORT) || 3306,
        user: process.env.DB_USER || 'root',
        // Environment files often acquire a trailing space when copied from a UI.
        password: (process.env.DB_PASSWORD || '').trim(),
        database: process.env.DB_NAME || 'healthconnect_pharmacy',
        waitForConnections: true,
        connectionLimit: 10,
        queueLimit: 0
      });

      // Test MySQL connection
      const connection = await pool.getConnection();
      console.log('✅ Connected to MySQL Database successfully.');
      connection.release();
      activeDriver = 'mysql';
      return;
    } catch (err) {
      // A silent fallback can make a production deployment appear to use MySQL
      // while it continues writing to a local JSON file. Fail clearly instead.
      throw new Error(`Could not connect to MySQL. Run \"npm run db:migrate\" after checking DB_HOST, DB_PORT, DB_USER, DB_PASSWORD and DB_NAME. (${err.code || err.message})`);
    }
  }

  if (dbType === 'file') {
    activeDriver = 'file';
    bootstrapFileStore();
    console.log(`✅ Initialized HealthConnect file-persisted database: ${storageFilePath}`);
    return;
  }

  throw new Error(`Unsupported DB_TYPE \"${dbType}\". Use \"mysql\" (or \"file\" only for local legacy data).`);
}

function bootstrapFileStore() {
  const dir = path.dirname(storageFilePath);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }

  if (fs.existsSync(storageFilePath)) {
    try {
      const raw = fs.readFileSync(storageFilePath, 'utf8');
      memoryDb = JSON.parse(raw);
      console.log('📂 Loaded existing database file from disk.');
      return;
    } catch (e) {
      console.warn('Could not read existing json storage, re-seeding...');
    }
  }

  // Seed default data if file does not exist yet
  const hash = bcrypt.hashSync('password123', 10);

  memoryDb.pharmacists = [
    { id: 1, name: 'Dr. Sarah Jenkins', email: 'pharmacist@healthconnect.com', password_hash: hash, created_at: new Date() },
    { id: 2, name: 'Alex Rivera, PharmD', email: 'alex.rivera@healthconnect.com', password_hash: hash, created_at: new Date() }
  ];

  memoryDb.medicines = [
    { id: 1, code: 'MED-1001', name: 'Paracetamol 500mg', unit: 'Tablets', stock_qty: 150, min_threshold: 50, created_at: new Date() },
    { id: 2, code: 'MED-1002', name: 'Amoxicillin 250mg', unit: 'Capsules', stock_qty: 12, min_threshold: 30, created_at: new Date() },
    { id: 3, code: 'MED-1003', name: 'Ibuprofen 400mg', unit: 'Tablets', stock_qty: 0, min_threshold: 40, created_at: new Date() },
    { id: 4, code: 'MED-1004', name: 'Metformin 850mg', unit: 'Tablets', stock_qty: 80, min_threshold: 25, created_at: new Date() },
    { id: 5, code: 'MED-1005', name: 'Omeprazole 20mg', unit: 'Capsules', stock_qty: 5, min_threshold: 20, created_at: new Date() },
    { id: 6, code: 'MED-1006', name: 'Atorvastatin 10mg', unit: 'Tablets', stock_qty: 0, min_threshold: 15, created_at: new Date() },
    { id: 7, code: 'MED-1007', name: 'Azithromycin 500mg', unit: 'Tablets', stock_qty: 65, min_threshold: 20, created_at: new Date() },
    { id: 8, code: 'MED-1008', name: 'Ciprofloxacin 500mg', unit: 'Tablets', stock_qty: 8, min_threshold: 15, created_at: new Date() }
  ];

  memoryDb.stock_transactions = [
    { id: 1, medicine_id: 1, qty_received: 100, batch_ref: 'BATCH-2026-001', pharmacist_id: 1, created_at: new Date(Date.now() - 5*86400000) },
    { id: 2, medicine_id: 1, qty_received: 50, batch_ref: 'BATCH-2026-015', pharmacist_id: 1, created_at: new Date(Date.now() - 2*86400000) },
    { id: 3, medicine_id: 2, qty_received: 20, batch_ref: 'BATCH-2026-009', pharmacist_id: 2, created_at: new Date(Date.now() - 4*86400000) },
    { id: 4, medicine_id: 4, qty_received: 80, batch_ref: 'BATCH-2026-003', pharmacist_id: 1, created_at: new Date(Date.now() - 3*86400000) }
  ];

  memoryDb.prescriptions = [
    { id: 1, patient_name: 'John Doe', doctor_name: 'Dr. Emily Vance', status: 'Pending', created_at: new Date(Date.now() - 3*3600000) },
    { id: 2, patient_name: 'Maria Garcia', doctor_name: 'Dr. Robert Chen', status: 'Pending', created_at: new Date(Date.now() - 1*3600000) },
    { id: 3, patient_name: 'Michael Smith', doctor_name: 'Dr. Emily Vance', status: 'Pending', created_at: new Date(Date.now() - 1800000) },
    { id: 4, patient_name: 'Alice Johnson', doctor_name: 'Dr. Gregory House', status: 'Dispensed', created_at: new Date(Date.now() - 86400000) }
  ];

  memoryDb.prescription_items = [
    { id: 1, prescription_id: 1, medicine_id: 1, dosage: '500mg', frequency: 'Twice daily', duration: '5 days', quantity: 10, dispensed: 0, dispensed_at: null, dispensed_by: null },
    { id: 2, prescription_id: 1, medicine_id: 2, dosage: '250mg', frequency: 'Three times daily', duration: '7 days', quantity: 21, dispensed: 0, dispensed_at: null, dispensed_by: null },
    { id: 3, prescription_id: 2, medicine_id: 4, dosage: '850mg', frequency: 'Once daily', duration: '30 days', quantity: 30, dispensed: 0, dispensed_at: null, dispensed_by: null },
    { id: 4, prescription_id: 2, medicine_id: 5, dosage: '20mg', frequency: 'Once daily before breakfast', duration: '14 days', quantity: 14, dispensed: 0, dispensed_at: null, dispensed_by: null },
    { id: 5, prescription_id: 3, medicine_id: 7, dosage: '500mg', frequency: 'Once daily', duration: '3 days', quantity: 3, dispensed: 0, dispensed_at: null, dispensed_by: null },
    { id: 6, prescription_id: 4, medicine_id: 1, dosage: '500mg', frequency: 'Every 6 hours as needed', duration: '3 days', quantity: 12, dispensed: 1, dispensed_at: new Date(Date.now() - 86400000), dispensed_by: 1 }
  ];

  saveToDisk();
}

function saveToDisk() {
  if (activeDriver === 'file') {
    try {
      fs.writeFileSync(storageFilePath, JSON.stringify(memoryDb, null, 2), 'utf8');
    } catch (e) {
      console.error('Error saving storage to disk:', e);
    }
  }
}

// Unified query wrapper for MySQL and Pure JS engine
async function query(sql, params = []) {
  if (activeDriver === 'mysql' && pool) {
    const [rows] = await pool.query(sql, params);
    return rows;
  }

  // Pure JS Engine SQL Parser
  const result = parseAndExecuteMemoryQuery(sql, params);
  const cleanSql = sql.trim().toUpperCase();
  if (cleanSql.startsWith('INSERT') || cleanSql.startsWith('UPDATE') || cleanSql.startsWith('DELETE')) {
    saveToDisk();
  }
  return result;
}

function parseAndExecuteMemoryQuery(sql, params) {
  const cleanSql = sql.trim().replace(/\s+/g, ' ');
  const upperSql = cleanSql.toUpperCase();

  // SELECT queries
  if (upperSql.startsWith('SELECT')) {
    // 1. Pharmacists by email
    if (upperSql.includes('FROM PHARMACISTS WHERE EMAIL =')) {
      const email = params[0];
      return memoryDb.pharmacists.filter(p => p.email.toLowerCase() === String(email).toLowerCase());
    }
    // 2. Pharmacists by id
    if (upperSql.includes('FROM PHARMACISTS WHERE ID =')) {
      return memoryDb.pharmacists.filter(p => p.id === Number(params[0]));
    }
    // 3. Count queries (handles COUNT(*) as count, total, low, out_of_stock, pending)
    if (upperSql.includes('COUNT(*)')) {
      if (upperSql.includes('FROM MEDICINES')) {
        if (upperSql.includes('STOCK_QTY <= 0') || upperSql.includes('STOCK_QTY = 0')) {
          const count = memoryDb.medicines.filter(m => m.stock_qty <= 0).length;
          return [{ count, total: count, low: count, out_of_stock: count, pending: count }];
        }
        if (upperSql.includes('MIN_THRESHOLD')) {
          const count = memoryDb.medicines.filter(m => m.stock_qty > 0 && m.stock_qty <= m.min_threshold).length;
          return [{ count, total: count, low: count, out_of_stock: count, pending: count }];
        }
        const count = memoryDb.medicines.length;
        return [{ count, total: count, low: count, out_of_stock: count, pending: count }];
      }
      if (upperSql.includes('FROM PRESCRIPTIONS')) {
        if (upperSql.includes('PENDING')) {
          const count = memoryDb.prescriptions.filter(p => p.status === 'Pending').length;
          return [{ count, total: count, low: count, out_of_stock: count, pending: count }];
        }
        const count = memoryDb.prescriptions.length;
        return [{ count, total: count, low: count, out_of_stock: count, pending: count }];
      }
      if (upperSql.includes('FROM PRESCRIPTION_ITEMS')) {
        const pId = Number(params[0]);
        const pending = memoryDb.prescription_items.filter(i => i.prescription_id === pId && !i.dispensed).length;
        return [{ count: pending, total: pending, low: pending, out_of_stock: pending, pending }];
      }
    }
    // 4. Active alerts list
    if (upperSql.includes('FROM MEDICINES WHERE STOCK_QTY <= MIN_THRESHOLD')) {
      let list = memoryDb.medicines.filter(m => m.stock_qty <= m.min_threshold).map(m => ({
        ...m,
        deficit: m.min_threshold - m.stock_qty
      }));
      if (upperSql.includes('STOCK_QTY > 0')) {
        list = list.filter(m => m.stock_qty > 0);
      } else if (upperSql.includes('STOCK_QTY <= 0') || upperSql.includes('STOCK_QTY = 0')) {
        list = list.filter(m => m.stock_qty <= 0);
      }
      return list;
    }
    // 5. Medicines list / search
    if (upperSql.includes('FROM MEDICINES')) {
      if (upperSql.includes('WHERE ID =')) {
        return memoryDb.medicines.filter(m => m.id === Number(params[0]));
      }
      if (upperSql.includes('WHERE CODE = ? OR NAME = ?')) {
        return memoryDb.medicines.filter(m => m.code === params[0] || m.name === params[1]);
      }
      if (upperSql.includes('WHERE CODE = ?')) {
        return memoryDb.medicines.filter(m => m.code === params[0]);
      }
      if (upperSql.includes('WHERE NAME LIKE ? OR CODE LIKE ?')) {
        const term = params[0].replace(/%/g, '').toLowerCase();
        return memoryDb.medicines.filter(m => m.name.toLowerCase().includes(term) || m.code.toLowerCase().includes(term));
      }
      return [...memoryDb.medicines].sort((a, b) => a.name.localeCompare(b.name));
    }
    // 6. Recent Transactions (Join)
    if (upperSql.includes('FROM STOCK_TRANSACTIONS')) {
      if (upperSql.includes('WHERE T.MEDICINE_ID =') || upperSql.includes('WHERE MEDICINE_ID =')) {
        const medId = Number(params[0]);
        return memoryDb.stock_transactions
          .filter(t => t.medicine_id === medId)
          .map(t => {
            const ph = memoryDb.pharmacists.find(p => p.id === t.pharmacist_id);
            return { ...t, pharmacist_name: ph ? ph.name : 'Pharmacist' };
          })
          .sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
      }
      return memoryDb.stock_transactions
        .map(t => {
          const med = memoryDb.medicines.find(m => m.id === t.medicine_id);
          const ph = memoryDb.pharmacists.find(p => p.id === t.pharmacist_id);
          return {
            ...t,
            medicine_name: med ? med.name : 'Unknown',
            unit: med ? med.unit : 'Units',
            pharmacist_name: ph ? ph.name : 'Pharmacist'
          };
        })
        .sort((a, b) => new Date(b.created_at) - new Date(a.created_at))
        .slice(0, 6);
    }
    // 7. Prescriptions List
    if (upperSql.includes('FROM PRESCRIPTIONS')) {
      if (upperSql.includes('WHERE ID =')) {
        return memoryDb.prescriptions.filter(p => p.id === Number(params[0]));
      }
      if (upperSql.includes('WHERE STATUS =')) {
        return memoryDb.prescriptions.filter(p => p.status === params[0]);
      }
      return [...memoryDb.prescriptions].sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
    }
    // 8. Prescription Items Join
    if (upperSql.includes('FROM PRESCRIPTION_ITEMS')) {
      if (upperSql.includes('WHERE PI.ID = ? AND PI.PRESCRIPTION_ID = ?') || upperSql.includes('WHERE ID = ? AND PRESCRIPTION_ID = ?')) {
        const itemId = Number(params[0]);
        const pId = Number(params[1]);
        return memoryDb.prescription_items
          .filter(i => i.id === itemId && i.prescription_id === pId)
          .map(i => {
            const med = memoryDb.medicines.find(m => m.id === i.medicine_id);
            return {
              ...i,
              medicine_name: med ? med.name : '',
              med_name: med ? med.name : '',
              unit: med ? med.unit : '',
              stock_qty: med ? med.stock_qty : 0,
              min_threshold: med ? med.min_threshold : 0
            };
          });
      }
      if (upperSql.includes('DISPENSED = FALSE') || upperSql.includes('DISPENSED = 0')) {
        const pId = Number(params[0]);
        return memoryDb.prescription_items
          .filter(i => i.prescription_id === pId && !i.dispensed)
          .map(i => {
            const med = memoryDb.medicines.find(m => m.id === i.medicine_id);
            return {
              ...i,
              medicine_name: med ? med.name : '',
              med_name: med ? med.name : '',
              unit: med ? med.unit : '',
              stock_qty: med ? med.stock_qty : 0,
              min_threshold: med ? med.min_threshold : 0
            };
          });
      }
      if (upperSql.includes('WHERE PI.PRESCRIPTION_ID =') || upperSql.includes('WHERE PRESCRIPTION_ID =')) {
        const pId = Number(params[0]);
        return memoryDb.prescription_items
          .filter(i => i.prescription_id === pId)
          .map(i => {
            const med = memoryDb.medicines.find(m => m.id === i.medicine_id);
            const ph = memoryDb.pharmacists.find(p => p.id === i.dispensed_by);
            return {
              ...i,
              medicine_name: med ? med.name : 'Medicine',
              med_name: med ? med.name : 'Medicine',
              medicine_code: med ? med.code : 'CODE',
              unit: med ? med.unit : 'Units',
              stock_qty: med ? med.stock_qty : 0,
              min_threshold: med ? med.min_threshold : 0,
              dispensed_by_name: ph ? ph.name : null
            };
          });
      }
    }
  }

  // INSERT queries
  if (upperSql.startsWith('INSERT')) {
    if (upperSql.includes('INTO MEDICINES')) {
      const newId = memoryDb.medicines.length + 1;
      const newMed = {
        id: newId,
        code: params[0],
        name: params[1],
        unit: params[2],
        stock_qty: params[3],
        min_threshold: params[4],
        created_at: new Date()
      };
      memoryDb.medicines.push(newMed);
      return { insertId: newId, affectedRows: 1 };
    }
    if (upperSql.includes('INTO STOCK_TRANSACTIONS')) {
      const newId = memoryDb.stock_transactions.length + 1;
      memoryDb.stock_transactions.push({
        id: newId,
        medicine_id: params[0],
        qty_received: params[1],
        batch_ref: params[2],
        pharmacist_id: params[3],
        created_at: new Date()
      });
      return { insertId: newId, affectedRows: 1 };
    }
  }

  // UPDATE queries
  if (upperSql.startsWith('UPDATE')) {
    if (upperSql.includes('UPDATE MEDICINES SET STOCK_QTY = STOCK_QTY +') || upperSql.includes('SET STOCK_QTY = ?')) {
      if (upperSql.includes('STOCK_QTY +')) {
        const qtyAdd = params[0];
        const medId = Number(params[1]);
        const med = memoryDb.medicines.find(m => m.id === medId);
        if (med) med.stock_qty += qtyAdd;
      } else {
        const newQty = params[0];
        const medId = Number(params[1]);
        const med = memoryDb.medicines.find(m => m.id === medId);
        if (med) med.stock_qty = newQty;
      }
      return { affectedRows: 1 };
    }
    if (upperSql.includes('UPDATE MEDICINES SET STOCK_QTY = STOCK_QTY -')) {
      const qtySub = params[0];
      const medId = Number(params[1]);
      const med = memoryDb.medicines.find(m => m.id === medId);
      if (med) med.stock_qty -= qtySub;
      return { affectedRows: 1 };
    }
    if (upperSql.includes('UPDATE PRESCRIPTION_ITEMS SET DISPENSED =')) {
      const itemId = Number(params[params.length - 1]);
      const item = memoryDb.prescription_items.find(i => i.id === itemId);
      if (item) {
        item.dispensed = 1;
        item.dispensed_at = new Date().toISOString();
        if (params.length > 2) item.dispensed_by = params[1];
      }
      return { affectedRows: 1 };
    }
    if (upperSql.includes('UPDATE PRESCRIPTIONS SET STATUS = "DISPENSED"') || upperSql.includes("UPDATE PRESCRIPTIONS SET STATUS = 'DISPENSED'")) {
      const pId = Number(params[0]);
      const p = memoryDb.prescriptions.find(pr => pr.id === pId);
      if (p) p.status = 'Dispensed';
      return { affectedRows: 1 };
    }
  }

  return [];
}

async function transaction(callback) {
  if (activeDriver === 'mysql' && pool) {
    const connection = await pool.getConnection();
    await connection.beginTransaction();
    try {
      const customQuery = async (sql, params = []) => {
        const [rows] = await connection.query(sql, params);
        return rows;
      };
      const result = await callback(customQuery);
      await connection.commit();
      connection.release();
      return result;
    } catch (err) {
      await connection.rollback();
      connection.release();
      throw err;
    }
  } else {
    // Pure JS transaction callback
    const res = await callback(async (sql, params = []) => {
      return query(sql, params);
    });
    saveToDisk();
    return res;
  }
}

module.exports = {
  initDb,
  query,
  transaction,
  getDriver: () => activeDriver
};
