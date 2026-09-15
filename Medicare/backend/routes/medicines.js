const express = require('express');
const router = express.Router();
const db = require('../config/db');
const { verifyToken } = require('../middleware/auth');

/**
 * GET /api/medicines
 * Search and list medicines by name or code
 * Query params: ?query=Paracetamol
 */
router.get('/', verifyToken, async (req, res) => {
  try {
    const { query } = req.query;
    let sql = 'SELECT * FROM medicines';
    let params = [];

    if (query && query.trim() !== '') {
      const searchTerm = `%${query.trim()}%`;
      sql += ' WHERE name LIKE ? OR code LIKE ?';
      params = [searchTerm, searchTerm];
    }

    sql += ' ORDER BY name ASC';
    const medicines = await db.query(sql, params);

    // Annotate low-stock status
    const result = medicines.map(med => {
      const isOutOfStock = med.stock_qty <= 0;
      const isLowStock = !isOutOfStock && med.stock_qty <= med.min_threshold;
      return {
        ...med,
        status: isOutOfStock ? 'Out of Stock' : (isLowStock ? 'Low Stock' : 'Normal'),
        deficit: Math.max(0, med.min_threshold - med.stock_qty)
      };
    });

    return res.json({
      success: true,
      count: result.length,
      medicines: result
    });
  } catch (err) {
    console.error('Error fetching medicines:', err);
    return res.status(500).json({ success: false, message: 'Server error retrieving medicines list.' });
  }
});

/**
 * GET /api/medicines/alerts
 * Dedicated view listing all low-stock and out-of-stock medicines (PB12, PB07)
 * Query params: ?filter=all|low|out & sortBy=deficit|name|stock
 */
router.get('/alerts', verifyToken, async (req, res) => {
  try {
    const { filter = 'all', sortBy = 'deficit' } = req.query;

    let sql = 'SELECT * FROM medicines WHERE stock_qty <= min_threshold';
    let params = [];

    if (filter === 'low') {
      sql = 'SELECT * FROM medicines WHERE stock_qty > 0 AND stock_qty <= min_threshold';
    } else if (filter === 'out') {
      sql = 'SELECT * FROM medicines WHERE stock_qty = 0';
    }

    let medicines = await db.query(sql, params);

    // Annotate and compute deficit
    medicines = medicines.map(med => {
      const isOutOfStock = med.stock_qty <= 0;
      return {
        ...med,
        status: isOutOfStock ? 'Out of Stock' : 'Low Stock',
        deficit: med.min_threshold - med.stock_qty
      };
    });

    // Sorting logic
    if (sortBy === 'deficit') {
      medicines.sort((a, b) => b.deficit - a.deficit);
    } else if (sortBy === 'name') {
      medicines.sort((a, b) => a.name.localeCompare(b.name));
    } else if (sortBy === 'stock') {
      medicines.sort((a, b) => a.stock_qty - b.stock_qty);
    }

    return res.json({
      success: true,
      count: medicines.length,
      filter,
      sortBy,
      medicines
    });
  } catch (err) {
    console.error('Error fetching low/out-of-stock list:', err);
    return res.status(500).json({ success: false, message: 'Server error retrieving stock alerts.' });
  }
});

/**
 * GET /api/medicines/:id
 * Get single medicine details
 */
router.get('/:id', verifyToken, async (req, res) => {
  try {
    const medicines = await db.query('SELECT * FROM medicines WHERE id = ?', [req.params.id]);
    if (medicines.length === 0) {
      return res.status(404).json({ success: false, message: 'Medicine record not found.' });
    }

    const med = medicines[0];
    const isOutOfStock = med.stock_qty <= 0;
    const isLowStock = !isOutOfStock && med.stock_qty <= med.min_threshold;

    return res.json({
      success: true,
      medicine: {
        ...med,
        status: isOutOfStock ? 'Out of Stock' : (isLowStock ? 'Low Stock' : 'Normal'),
        deficit: Math.max(0, med.min_threshold - med.stock_qty)
      }
    });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'Server error retrieving medicine details.' });
  }
});

/**
 * POST /api/medicines
 * Add a new medicine record if it does not exist yet (PB06)
 */
router.post('/', verifyToken, async (req, res) => {
  try {
    const { code, name, unit, min_threshold, starting_stock } = req.body;

    // Validation
    if (!code || !name || !unit) {
      return res.status(400).json({
        success: false,
        message: 'Medicine code, name, and unit are required.'
      });
    }

    const parsedMinThreshold = parseInt(min_threshold, 10);
    const parsedStartingStock = parseInt(starting_stock || 0, 10);

    if (isNaN(parsedMinThreshold) || parsedMinThreshold < 0) {
      return res.status(400).json({
        success: false,
        message: 'Minimum threshold must be a valid non-negative integer.'
      });
    }

    if (isNaN(parsedStartingStock) || parsedStartingStock < 0) {
      return res.status(400).json({
        success: false,
        message: 'Starting stock must be a valid non-negative integer.'
      });
    }

    // Check code uniqueness
    const existing = await db.query('SELECT id FROM medicines WHERE code = ? OR name = ?', [code.trim(), name.trim()]);
    if (existing.length > 0) {
      return res.status(409).json({
        success: false,
        message: 'A medicine with this code or name already exists in the inventory system.'
      });
    }

    // Insert new medicine
    const result = await db.query(
      'INSERT INTO medicines (code, name, unit, stock_qty, min_threshold) VALUES (?, ?, ?, ?, ?)',
      [code.trim().toUpperCase(), name.trim(), unit.trim(), parsedStartingStock, parsedMinThreshold]
    );

    const newId = result.insertId;

    // Log initial stock transaction if starting stock > 0
    if (parsedStartingStock > 0) {
      await db.query(
        'INSERT INTO stock_transactions (medicine_id, qty_received, batch_ref, pharmacist_id) VALUES (?, ?, ?, ?)',
        [newId, parsedStartingStock, 'INITIAL-SET-UP', req.user.id]
      );
    }

    const isLowStock = parsedStartingStock <= parsedMinThreshold;

    return res.status(201).json({
      success: true,
      message: `Medicine '${name}' added successfully to inventory.`,
      medicine: {
        id: newId,
        code: code.trim().toUpperCase(),
        name: name.trim(),
        unit: unit.trim(),
        stock_qty: parsedStartingStock,
        min_threshold: parsedMinThreshold
      },
      lowStockAlert: isLowStock,
      alertMessage: isLowStock ? `Warning: Starting stock (${parsedStartingStock}) is below minimum threshold (${parsedMinThreshold}).` : null
    });
  } catch (err) {
    console.error('Error adding new medicine:', err);
    return res.status(500).json({ success: false, message: 'Server error creating new medicine record.' });
  }
});

/**
 * POST /api/medicines/:id/stock
 * Add received stock to an existing medicine (PB06) & Check Low-Stock Alerts (PB07)
 */
router.post('/:id/stock', verifyToken, async (req, res) => {
  try {
    const medicineId = parseInt(req.params.id, 10);
    const { qty_received, batch_ref } = req.body;

    // Input validation: reject negative or non-numeric quantities (Edge Case)
    if (qty_received === undefined || qty_received === null || String(qty_received).trim() === '') {
      return res.status(400).json({
        success: false,
        message: 'Received quantity is required.'
      });
    }

    const parsedQty = Number(qty_received);
    if (!Number.isInteger(parsedQty) || parsedQty <= 0) {
      return res.status(400).json({
        success: false,
        message: 'Invalid quantity: Stock addition quantity must be a positive integer greater than zero.'
      });
    }

    if (!batch_ref || batch_ref.trim() === '') {
      return res.status(400).json({
        success: false,
        message: 'Supplier or batch reference code is required for stock transaction logging.'
      });
    }

    // Verify medicine existence
    const medicines = await db.query('SELECT * FROM medicines WHERE id = ?', [medicineId]);
    if (medicines.length === 0) {
      return res.status(404).json({
        success: false,
        message: 'Medicine record not found in system.'
      });
    }

    const currentMed = medicines[0];

    // Perform transaction: Log stock transaction + Update stock quantity
    await db.transaction(async (txQuery) => {
      // 1. Log transaction record
      await txQuery(
        'INSERT INTO stock_transactions (medicine_id, qty_received, batch_ref, pharmacist_id) VALUES (?, ?, ?, ?)',
        [medicineId, parsedQty, batch_ref.trim(), req.user.id]
      );

      // 2. Add to existing stock level
      await txQuery(
        'UPDATE medicines SET stock_qty = stock_qty + ? WHERE id = ?',
        [parsedQty, medicineId]
      );
    });

    // Fetch updated medicine record
    const updatedMeds = await db.query('SELECT * FROM medicines WHERE id = ?', [medicineId]);
    const updatedMed = updatedMeds[0];

    // PB07: Low-Stock Alert check after update
    const stillBelowThreshold = updatedMed.stock_qty <= updatedMed.min_threshold;

    return res.json({
      success: true,
      message: `Successfully added ${parsedQty} ${updatedMed.unit} of ${updatedMed.name} to stock.`,
      medicine: updatedMed,
      previous_stock: currentMed.stock_qty,
      new_stock: updatedMed.stock_qty,
      lowStockAlert: stillBelowThreshold,
      alertMessage: stillBelowThreshold
        ? `⚠️ LOW STOCK ALERT: Updated quantity (${updatedMed.stock_qty} ${updatedMed.unit}) is STILL below the configured minimum threshold (${updatedMed.min_threshold} ${updatedMed.unit})!`
        : null
    });
  } catch (err) {
    console.error('Error updating stock:', err);
    return res.status(500).json({ success: false, message: 'Server error updating medicine stock level.' });
  }
});

/**
 * GET /api/medicines/:id/transactions
 * Retrieve transaction audit trail for a medicine
 */
router.get('/:id/transactions', verifyToken, async (req, res) => {
  try {
    const medicineId = parseInt(req.params.id, 10);
    const sql = `
      SELECT t.id, t.qty_received, t.batch_ref, t.created_at,
             p.name AS pharmacist_name
      FROM stock_transactions t
      LEFT JOIN pharmacists p ON t.pharmacist_id = p.id
      WHERE t.medicine_id = ?
      ORDER BY t.created_at DESC
    `;
    const transactions = await db.query(sql, [medicineId]);

    return res.json({
      success: true,
      count: transactions.length,
      transactions
    });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'Server error retrieving transaction logs.' });
  }
});

module.exports = router;
