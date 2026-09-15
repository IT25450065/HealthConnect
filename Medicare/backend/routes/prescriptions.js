const express = require('express');
const router = express.Router();
const db = require('../config/db');
const { verifyToken } = require('../middleware/auth');

/**
 * GET /api/prescriptions
 * View queue/list of prescriptions with items and stock status
 * Query params: ?status=Pending|Dispensed|All
 */
router.get('/', verifyToken, async (req, res) => {
  try {
    const { status = 'All' } = req.query;

    let sql = 'SELECT * FROM prescriptions';
    let params = [];

    if (status !== 'All') {
      sql += ' WHERE status = ?';
      params.push(status);
    }

    sql += ' ORDER BY created_at DESC';
    const prescriptions = await db.query(sql, params);

    // Fetch items for each prescription
    const result = await Promise.all(
      prescriptions.map(async (p) => {
        const itemSql = `
          SELECT pi.id, pi.prescription_id, pi.medicine_id, pi.dosage, pi.frequency,
                 pi.duration, pi.quantity, pi.dispensed, pi.dispensed_at,
                 m.name AS medicine_name, m.code AS medicine_code, m.unit,
                 m.stock_qty, m.min_threshold,
                 ph.name AS dispensed_by_name
          FROM prescription_items pi
          JOIN medicines m ON pi.medicine_id = m.id
          LEFT JOIN pharmacists ph ON pi.dispensed_by = ph.id
          WHERE pi.prescription_id = ?
        `;
        const items = await db.query(itemSql, [p.id]);

        // Evaluate item stock availability for dispensing
        const itemsWithStockStatus = items.map(item => {
          const isDispensed = Boolean(item.dispensed);
          const hasSufficientStock = item.stock_qty >= item.quantity;
          let stockCheck = 'Sufficient';
          if (!isDispensed && item.stock_qty === 0) {
            stockCheck = 'Out of Stock';
          } else if (!isDispensed && !hasSufficientStock) {
            stockCheck = 'Insufficient Stock';
          } else if (!isDispensed && item.stock_qty <= item.min_threshold) {
            stockCheck = 'Low Stock (Available)';
          }

          return {
            ...item,
            dispensed: isDispensed,
            hasSufficientStock,
            stockCheck
          };
        });

        const pendingItemsCount = itemsWithStockStatus.filter(i => !i.dispensed).length;
        const totalItemsCount = itemsWithStockStatus.length;

        return {
          ...p,
          total_items: totalItemsCount,
          pending_items: pendingItemsCount,
          items: itemsWithStockStatus
        };
      })
    );

    return res.json({
      success: true,
      count: result.length,
      statusFilter: status,
      prescriptions: result
    });
  } catch (err) {
    console.error('Error fetching prescriptions queue:', err);
    return res.status(500).json({ success: false, message: 'Server error retrieving prescriptions.' });
  }
});

/**
 * GET /api/prescriptions/:id
 * Retrieve single prescription queue item detail
 */
router.get('/:id', verifyToken, async (req, res) => {
  try {
    const pId = parseInt(req.params.id, 10);
    const prescriptions = await db.query('SELECT * FROM prescriptions WHERE id = ?', [pId]);
    if (prescriptions.length === 0) {
      return res.status(404).json({ success: false, message: 'Prescription not found.' });
    }

    const prescription = prescriptions[0];
    const itemSql = `
      SELECT pi.id, pi.prescription_id, pi.medicine_id, pi.dosage, pi.frequency,
             pi.duration, pi.quantity, pi.dispensed, pi.dispensed_at,
             m.name AS medicine_name, m.code AS medicine_code, m.unit,
             m.stock_qty, m.min_threshold,
             ph.name AS dispensed_by_name
      FROM prescription_items pi
      JOIN medicines m ON pi.medicine_id = m.id
      LEFT JOIN pharmacists ph ON pi.dispensed_by = ph.id
      WHERE pi.prescription_id = ?
    `;
    const items = await db.query(itemSql, [pId]);

    const itemsWithStatus = items.map(item => ({
      ...item,
      dispensed: Boolean(item.dispensed),
      hasSufficientStock: item.stock_qty >= item.quantity
    }));

    return res.json({
      success: true,
      prescription: {
        ...prescription,
        items: itemsWithStatus
      }
    });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'Server error fetching prescription.' });
  }
});

/**
 * POST /api/prescriptions/:id/dispense-item/:itemId
 * Dispense a specific line item in a prescription (PB13)
 * Automatically deducts stock and prevents negative stock (Edge Case)
 */
router.post('/:id/dispense-item/:itemId', verifyToken, async (req, res) => {
  try {
    const prescriptionId = parseInt(req.params.id, 10);
    const itemId = parseInt(req.params.itemId, 10);

    // Fetch line item and medicine record
    const itemSql = `
      SELECT pi.*, m.name AS medicine_name, m.unit, m.stock_qty, m.min_threshold
      FROM prescription_items pi
      JOIN medicines m ON pi.medicine_id = m.id
      WHERE pi.id = ? AND pi.prescription_id = ?
    `;
    const items = await db.query(itemSql, [itemId, prescriptionId]);

    if (items.length === 0) {
      return res.status(404).json({
        success: false,
        message: 'Prescription line item not found.'
      });
    }

    const item = items[0];

    // Check if already dispensed
    if (Boolean(item.dispensed)) {
      return res.status(400).json({
        success: false,
        message: `This item (${item.medicine_name}) has already been dispensed.`
      });
    }

    // EDGE CASE: Block dispensing if stock is insufficient!
    if (item.stock_qty < item.quantity) {
      return res.status(400).json({
        success: false,
        insufficientStock: true,
        message: `Cannot dispense item: Insufficient stock for ${item.medicine_name}. Prescribed quantity: ${item.quantity} ${item.unit}, but only ${item.stock_qty} ${item.unit} available in inventory.`
      });
    }

    // Execute atomic transaction for stock deduction & item update
    let newStockLevel = 0;
    await db.transaction(async (txQuery) => {
      // 1. Deduct dispensed quantity from medicine stock
      await txQuery(
        'UPDATE medicines SET stock_qty = stock_qty - ? WHERE id = ?',
        [item.quantity, item.medicine_id]
      );

      // 2. Mark item as dispensed
      const nowStr = new Date().toISOString().slice(0, 19).replace('T', ' ');
      await txQuery(
        'UPDATE prescription_items SET dispensed = 1, dispensed_at = ?, dispensed_by = ? WHERE id = ?',
        [nowStr, req.user.id, itemId]
      );

      // 3. Check if all items in this prescription are now dispensed
      const remainingPending = await txQuery(
        'SELECT COUNT(*) as count FROM prescription_items WHERE prescription_id = ? AND (dispensed = 0 OR dispensed IS NULL)',
        [prescriptionId]
      );

      const pendingCount = remainingPending[0].count;
      if (pendingCount === 0) {
        await txQuery('UPDATE prescriptions SET status = "Dispensed" WHERE id = ?', [prescriptionId]);
      }
    });

    // Get updated stock
    const updatedMeds = await db.query('SELECT stock_qty, min_threshold FROM medicines WHERE id = ?', [item.medicine_id]);
    newStockLevel = updatedMeds[0].stock_qty;
    const isLowStockAlert = newStockLevel <= updatedMeds[0].min_threshold;

    return res.json({
      success: true,
      message: `Dispensed ${item.quantity} ${item.unit} of ${item.medicine_name} successfully. Stock updated from ${item.stock_qty} to ${newStockLevel}.`,
      item_id: itemId,
      prescription_id: prescriptionId,
      medicine_name: item.medicine_name,
      deducted_qty: item.quantity,
      remaining_stock: newStockLevel,
      lowStockAlert: isLowStockAlert,
      alertMessage: isLowStockAlert
        ? `⚠️ LOW STOCK ALERT: Stock for ${item.medicine_name} is now at ${newStockLevel} ${item.unit} (below threshold of ${updatedMeds[0].min_threshold})!`
        : null
    });
  } catch (err) {
    console.error('Error dispensing prescription item:', err);
    return res.status(500).json({ success: false, message: 'Server error during prescription dispensing.' });
  }
});

/**
 * POST /api/prescriptions/:id/dispense
 * Dispense all pending items for an entire prescription (PB13)
 */
router.post('/:id/dispense', verifyToken, async (req, res) => {
  try {
    const prescriptionId = parseInt(req.params.id, 10);

    const itemSql = `
      SELECT pi.*, m.name AS medicine_name, m.unit, m.stock_qty
      FROM prescription_items pi
      JOIN medicines m ON pi.medicine_id = m.id
      WHERE pi.prescription_id = ? AND (pi.dispensed = 0 OR pi.dispensed IS NULL)
    `;
    const pendingItems = await db.query(itemSql, [prescriptionId]);

    if (pendingItems.length === 0) {
      return res.status(400).json({
        success: false,
        message: 'No pending items found for this prescription (or all items already dispensed).'
      });
    }

    // Check if ALL pending items have sufficient stock before processing
    const insufficientList = [];
    for (const item of pendingItems) {
      if (item.stock_qty < item.quantity) {
        insufficientList.push({
          medicine: item.medicine_name,
          required: item.quantity,
          available: item.stock_qty,
          unit: item.unit
        });
      }
    }

    if (insufficientList.length > 0) {
      const details = insufficientList.map(i => `${i.medicine} (Need: ${i.required} ${i.unit}, Have: ${i.available} ${i.unit})`).join('; ');
      return res.status(400).json({
        success: false,
        insufficientStock: true,
        message: `Cannot dispense prescription: Insufficient stock for item(s): ${details}`
      });
    }

    // Process all pending items in transaction
    await db.transaction(async (txQuery) => {
      const nowStr = new Date().toISOString().slice(0, 19).replace('T', ' ');

      for (const item of pendingItems) {
        // Deduct stock
        await txQuery('UPDATE medicines SET stock_qty = stock_qty - ? WHERE id = ?', [item.quantity, item.medicine_id]);

        // Mark item dispensed
        await txQuery('UPDATE prescription_items SET dispensed = 1, dispensed_at = ?, dispensed_by = ? WHERE id = ?', [nowStr, req.user.id, item.id]);
      }

      // Mark parent prescription as Dispensed
      await txQuery('UPDATE prescriptions SET status = "Dispensed" WHERE id = ?', [prescriptionId]);
    });

    return res.json({
      success: true,
      message: `Prescription #${prescriptionId} fully dispensed! All line items updated and stock levels deducted.`,
      dispensedItemsCount: pendingItems.length
    });
  } catch (err) {
    console.error('Error dispensing entire prescription:', err);
    return res.status(500).json({ success: false, message: 'Server error processing full prescription dispensing.' });
  }
});

module.exports = router;
