const db = require('../config/db');

/**
 * HealthConnect Pharmacy Module Controller (Ahamed's Module)
 * Handles stock management (PB06), low-stock alerts (PB07/PB12),
 * and prescription dispensing queue (PB13).
 */

// 1. Get All Medicines & Live Search (PB06)
exports.getAllMedicines = async (req, res) => {
  try {
    const { query: searchQuery } = req.query;
    let sql = 'SELECT * FROM medicines';
    let params = [];

    if (searchQuery && searchQuery.trim() !== '') {
      sql += ' WHERE name LIKE ? OR code LIKE ?';
      const term = `%${searchQuery.trim()}%`;
      params = [term, term];
    }

    sql += ' ORDER BY name ASC';
    const medicines = await db.query(sql, params);

    const formatted = medicines.map(med => {
      let status = 'Normal';
      if (med.stock_qty <= 0) status = 'Out of Stock';
      else if (med.stock_qty <= med.min_threshold) status = 'Low Stock';

      return {
        ...med,
        status,
        deficit: med.stock_qty < med.min_threshold ? (med.min_threshold - med.stock_qty) : 0
      };
    });

    res.json({ success: true, medicines: formatted });
  } catch (err) {
    console.error('Error fetching medicines:', err);
    res.status(500).json({ success: false, message: 'Server error retrieving medicines inventory.' });
  }
};

// 2. Add New Medicine Record (PB06)
exports.addMedicine = async (req, res) => {
  try {
    const { code, name, unit, min_threshold, starting_stock } = req.body;

    if (!code || !name || !unit || min_threshold === undefined || starting_stock === undefined) {
      return res.status(400).json({ success: false, message: 'All required fields must be provided.' });
    }

    const minVal = parseInt(min_threshold, 10);
    const stockVal = parseInt(starting_stock, 10);

    if (isNaN(minVal) || minVal < 0 || isNaN(stockVal) || stockVal < 0) {
      return res.status(400).json({ success: false, message: 'Threshold and starting stock must be non-negative integers.' });
    }

    const existing = await db.query('SELECT id FROM medicines WHERE code = ?', [code.trim()]);
    if (existing.length > 0) {
      return res.status(400).json({ success: false, message: `Medicine code "${code}" already exists in database.` });
    }

    const insertResult = await db.query(
      'INSERT INTO medicines (code, name, unit, min_threshold, stock_qty) VALUES (?, ?, ?, ?, ?)',
      [code.trim(), name.trim(), unit.trim(), minVal, stockVal]
    );

    const newMedId = insertResult.insertId;

    if (stockVal > 0) {
      await db.query(
        'INSERT INTO stock_transactions (medicine_id, qty_received, batch_ref, pharmacist_id) VALUES (?, ?, ?, ?)',
        [newMedId, stockVal, 'INITIAL-STOCK', req.user.id]
      );
    }

    const isLowStock = stockVal <= minVal;

    res.status(201).json({
      success: true,
      message: `Medicine "${name}" created successfully.`,
      medicineId: newMedId,
      lowStockAlert: isLowStock,
      alertMessage: isLowStock ? `⚠️ Low Stock Alert (PB07): Starting stock (${stockVal}) is at or below minimum threshold (${minVal}).` : null
    });
  } catch (err) {
    console.error('Error adding medicine:', err);
    res.status(500).json({ success: false, message: 'Server error creating medicine record.' });
  }
};

// 3. Receive Stock Delivery (PB06 / PB07)
exports.restockMedicine = async (req, res) => {
  try {
    const { id } = req.params;
    const { qty_received, batch_ref } = req.body;

    const qty = parseInt(qty_received, 10);
    if (isNaN(qty) || qty <= 0) {
      return res.status(400).json({ success: false, message: 'Received quantity must be a positive integer greater than zero.' });
    }

    if (!batch_ref || batch_ref.trim() === '') {
      return res.status(400).json({ success: false, message: 'Supplier / Batch reference code is required.' });
    }

    const medRows = await db.query('SELECT * FROM medicines WHERE id = ?', [id]);
    if (medRows.length === 0) {
      return res.status(404).json({ success: false, message: 'Medicine not found.' });
    }

    const med = medRows[0];
    const newQty = med.stock_qty + qty;

    await db.query('UPDATE medicines SET stock_qty = ? WHERE id = ?', [newQty, id]);

    await db.query(
      'INSERT INTO stock_transactions (medicine_id, qty_received, batch_ref, pharmacist_id) VALUES (?, ?, ?, ?)',
      [id, qty, batch_ref.trim(), req.user.id]
    );

    const stillLowStock = newQty <= med.min_threshold;

    res.json({
      success: true,
      message: `Successfully added +${qty} ${med.unit} to ${med.name}. New total: ${newQty} ${med.unit}.`,
      updatedStock: newQty,
      lowStockAlert: stillLowStock,
      alertMessage: stillLowStock ? `⚠️ Stock updated, but ${med.name} (${newQty} ${med.unit}) remains at or below threshold (${med.min_threshold} ${med.unit}).` : null
    });
  } catch (err) {
    console.error('Error restocking medicine:', err);
    res.status(500).json({ success: false, message: 'Server error adding stock delivery.' });
  }
};

// 4. Get Low & Out-of-Stock Alerts List (PB07 / PB12)
exports.getLowStockAlerts = async (req, res) => {
  try {
    const { filter = 'all', sortBy = 'deficit' } = req.query;

    let sql = 'SELECT * FROM medicines WHERE stock_qty <= min_threshold';
    const params = [];

    if (filter === 'low') {
      sql += ' AND stock_qty > 0';
    } else if (filter === 'out') {
      sql += ' AND stock_qty <= 0';
    }

    if (sortBy === 'name') {
      sql += ' ORDER BY name ASC';
    } else if (sortBy === 'stock') {
      sql += ' ORDER BY stock_qty ASC';
    } else {
      sql += ' ORDER BY (min_threshold - stock_qty) DESC';
    }

    const medicines = await db.query(sql, params);

    const formatted = medicines.map(med => {
      const deficit = med.min_threshold - med.stock_qty;
      const isOut = med.stock_qty <= 0;

      return {
        ...med,
        deficit: deficit > 0 ? deficit : 0,
        status: isOut ? 'Out of Stock' : 'Low Stock',
        severity: isOut ? 'critical' : 'warning'
      };
    });

    res.json({ success: true, medicines: formatted });
  } catch (err) {
    console.error('Error fetching low stock alerts:', err);
    res.status(500).json({ success: false, message: 'Server error retrieving low stock alerts.' });
  }
};

// 5. Get Prescription Dispensing Queue (PB13)
exports.getPrescriptionsQueue = async (req, res) => {
  try {
    const { status } = req.query;
    let sql = 'SELECT * FROM prescriptions';
    let params = [];

    if (status && status !== 'All') {
      sql += ' WHERE status = ?';
      params.push(status);
    }

    sql += ' ORDER BY created_at DESC';
    const prescriptions = await db.query(sql, params);

    const result = [];
    for (const p of prescriptions) {
      const items = await db.query(
        `SELECT pi.*, m.name as medicine_name, m.code as medicine_code, m.unit, m.stock_qty, m.min_threshold
         FROM prescription_items pi
         JOIN medicines m ON pi.medicine_id = m.id
         WHERE pi.prescription_id = ?`,
        [p.id]
      );

      const formattedItems = items.map(item => {
        const hasStock = item.stock_qty >= item.quantity;
        let stockCheck = 'Sufficient';
        if (item.stock_qty <= 0) stockCheck = 'Out of Stock';
        else if (item.stock_qty < item.quantity) stockCheck = 'Insufficient Stock';
        else if (item.stock_qty <= item.min_threshold) stockCheck = 'Low Stock (Available)';

        return {
          ...item,
          hasSufficientStock: hasStock,
          stockCheck
        };
      });

      result.push({
        ...p,
        items: formattedItems
      });
    }

    res.json({ success: true, prescriptions: result });
  } catch (err) {
    console.error('Error fetching prescriptions queue:', err);
    res.status(500).json({ success: false, message: 'Server error retrieving prescription queue.' });
  }
};

// 6. Dispense Single Line Item (PB13)
exports.dispensePrescriptionItem = async (req, res) => {
  try {
    const { prescriptionId, itemId } = req.params;

    const itemRows = await db.query(
      `SELECT pi.*, m.name as med_name, m.stock_qty, m.unit, m.min_threshold
       FROM prescription_items pi
       JOIN medicines m ON pi.medicine_id = m.id
       WHERE pi.id = ? AND pi.prescription_id = ?`,
      [itemId, prescriptionId]
    );

    if (itemRows.length === 0) {
      return res.status(404).json({ success: false, message: 'Prescription item not found.' });
    }

    const item = itemRows[0];
    if (item.dispensed) {
      return res.status(400).json({ success: false, message: 'This item has already been dispensed.' });
    }

    if (item.stock_qty < item.quantity) {
      return res.status(400).json({
        success: false,
        message: `Insufficient stock! Required: ${item.quantity} ${item.unit}, but current inventory has only ${item.stock_qty} ${item.unit}. Dispensing blocked.`
      });
    }

    const newStock = item.stock_qty - item.quantity;
    await db.query('UPDATE medicines SET stock_qty = ? WHERE id = ?', [newStock, item.medicine_id]);
    await db.query('UPDATE prescription_items SET dispensed = true, dispensed_at = CURRENT_TIMESTAMP WHERE id = ?', [itemId]);

    const remainingItems = await db.query('SELECT id FROM prescription_items WHERE prescription_id = ? AND dispensed = false', [prescriptionId]);
    if (remainingItems.length === 0) {
      await db.query('UPDATE prescriptions SET status = "Dispensed" WHERE id = ?', [prescriptionId]);
    }

    const triggerAlert = newStock <= item.min_threshold;

    res.json({
      success: true,
      message: `Successfully dispensed ${item.quantity} ${item.unit} of ${item.med_name}. Updated stock: ${newStock} ${item.unit}.`,
      lowStockAlert: triggerAlert,
      alertMessage: triggerAlert ? `⚠️ Alert (PB07): Stock for ${item.med_name} (${newStock} ${item.unit}) is now at or below threshold (${item.min_threshold} ${item.unit}).` : null
    });
  } catch (err) {
    console.error('Error dispensing item:', err);
    res.status(500).json({ success: false, message: 'Server error dispensing line item.' });
  }
};

// 7. Dispense Entire Prescription (PB13)
exports.dispenseEntirePrescription = async (req, res) => {
  try {
    const { prescriptionId } = req.params;

    const items = await db.query(
      `SELECT pi.*, m.name as med_name, m.stock_qty, m.unit
       FROM prescription_items pi
       JOIN medicines m ON pi.medicine_id = m.id
       WHERE pi.prescription_id = ? AND pi.dispensed = false`,
      [prescriptionId]
    );

    if (items.length === 0) {
      return res.status(400).json({ success: false, message: 'No pending items to dispense for this prescription.' });
    }

    for (const item of items) {
      if (item.stock_qty < item.quantity) {
        return res.status(400).json({
          success: false,
          message: `Cannot dispense entire prescription! "${item.med_name}" has insufficient stock (Required: ${item.quantity}, Available: ${item.stock_qty}).`
        });
      }
    }

    for (const item of items) {
      const newStock = item.stock_qty - item.quantity;
      await db.query('UPDATE medicines SET stock_qty = ? WHERE id = ?', [newStock, item.medicine_id]);
      await db.query('UPDATE prescription_items SET dispensed = true, dispensed_at = CURRENT_TIMESTAMP WHERE id = ?', [item.id]);
    }

    await db.query('UPDATE prescriptions SET status = "Dispensed" WHERE id = ?', [prescriptionId]);

    res.json({
      success: true,
      message: `Prescription #${prescriptionId} fully dispensed successfully.`
    });
  } catch (err) {
    console.error('Error dispensing entire prescription:', err);
    res.status(500).json({ success: false, message: 'Server error dispensing prescription.' });
  }
};

// 8. Live Dashboard Statistics & Overview
exports.getDashboardStats = async (req, res) => {
  try {
    const totalMedsRes = await db.query('SELECT COUNT(*) as total FROM medicines');
    const lowStockRes = await db.query('SELECT COUNT(*) as low FROM medicines WHERE stock_qty <= min_threshold AND stock_qty > 0');
    const outStockRes = await db.query('SELECT COUNT(*) as out_of_stock FROM medicines WHERE stock_qty <= 0');
    const pendingPrescRes = await db.query('SELECT COUNT(*) as pending FROM prescriptions WHERE status = "Pending"');

    const alerts = await db.query(
      'SELECT id, code, name, unit, stock_qty, min_threshold FROM medicines WHERE stock_qty <= min_threshold ORDER BY (min_threshold - stock_qty) DESC LIMIT 5'
    );

    const formattedAlerts = alerts.map(med => {
      const isOut = med.stock_qty <= 0;
      return {
        ...med,
        deficit: med.min_threshold - med.stock_qty,
        status: isOut ? 'Out of Stock' : 'Low Stock',
        severity: isOut ? 'critical' : 'warning'
      };
    });

    const recentTx = await db.query(
      `SELECT st.*, m.name as medicine_name, m.unit, p.name as pharmacist_name
       FROM stock_transactions st
       JOIN medicines m ON st.medicine_id = m.id
       LEFT JOIN pharmacists p ON st.pharmacist_id = p.id
       ORDER BY st.created_at DESC LIMIT 5`
    );

    res.json({
      success: true,
      stats: {
        totalMedicines: totalMedsRes[0].total,
        lowStockCount: lowStockRes[0].low,
        outOfStockCount: outStockRes[0].out_of_stock,
        pendingPrescriptionsCount: pendingPrescRes[0].pending
      },
      alerts: formattedAlerts,
      recentTransactions: recentTx
    });
  } catch (err) {
    console.error('Error getting dashboard stats:', err);
    res.status(500).json({ success: false, message: 'Server error loading dashboard statistics.' });
  }
};
