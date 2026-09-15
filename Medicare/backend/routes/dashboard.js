const express = require('express');
const router = express.Router();
const db = require('../config/db');
const { verifyToken } = require('../middleware/auth');

/**
 * GET /api/dashboard/stats
 * Overview KPI metrics & active low-stock alerts for Pharmacist Dashboard
 */
router.get('/stats', verifyToken, async (req, res) => {
  try {
    // 1. Total Medicines
    const totalMedRes = await db.query('SELECT COUNT(*) as count FROM medicines');
    const totalMedicines = totalMedRes[0].count;

    // 2. Out of Stock (stock_qty = 0)
    const outOfStockRes = await db.query('SELECT COUNT(*) as count FROM medicines WHERE stock_qty = 0');
    const outOfStockCount = outOfStockRes[0].count;

    // 3. Low Stock (0 < stock_qty <= min_threshold)
    const lowStockRes = await db.query('SELECT COUNT(*) as count FROM medicines WHERE stock_qty > 0 AND stock_qty <= min_threshold');
    const lowStockCount = lowStockRes[0].count;

    // 4. Pending Prescriptions
    const pendingPrescRes = await db.query('SELECT COUNT(*) as count FROM prescriptions WHERE status = "Pending"');
    const pendingPrescriptionsCount = pendingPrescRes[0].count;

    // 5. Active Low-Stock Alerts (PB07 list)
    const alerts = await db.query(`
      SELECT id, code, name, unit, stock_qty, min_threshold,
             (min_threshold - stock_qty) AS deficit
      FROM medicines
      WHERE stock_qty <= min_threshold
      ORDER BY (min_threshold - stock_qty) DESC, name ASC
    `);

    const alertList = alerts.map(med => ({
      ...med,
      status: med.stock_qty <= 0 ? 'Out of Stock' : 'Low Stock',
      severity: med.stock_qty <= 0 ? 'critical' : 'warning'
    }));

    // 6. Recent Stock Transactions (Audit Log)
    const recentTx = await db.query(`
      SELECT t.id, t.qty_received, t.batch_ref, t.created_at,
             m.name AS medicine_name, m.unit,
             p.name AS pharmacist_name
      FROM stock_transactions t
      JOIN medicines m ON t.medicine_id = m.id
      LEFT JOIN pharmacists p ON t.pharmacist_id = p.id
      ORDER BY t.created_at DESC
      LIMIT 6
    `);

    return res.json({
      success: true,
      stats: {
        totalMedicines,
        outOfStockCount,
        lowStockCount,
        totalAlertsCount: outOfStockCount + lowStockCount,
        pendingPrescriptionsCount
      },
      alerts: alertList,
      recentTransactions: recentTx
    });
  } catch (err) {
    console.error('Error fetching dashboard stats:', err);
    return res.status(500).json({ success: false, message: 'Server error retrieving dashboard statistics.' });
  }
});

module.exports = router;
