const express = require('express');
const router = express.Router();
const { verifyToken } = require('../middleware/auth');
const pharmacyController = require('../controllers/pharmacyController');

/**
 * HealthConnect Pharmacy Module Routes (Ahamed's Module)
 * Base Path: /api/pharmacy
 */

// Dashboard Stats & Alerts Summary
router.get('/dashboard/stats', verifyToken, pharmacyController.getDashboardStats);

// Inventory & Stock Management (PB06)
router.get('/medicines', verifyToken, pharmacyController.getAllMedicines);
router.post('/medicines', verifyToken, pharmacyController.addMedicine);
router.post('/medicines/:id/stock', verifyToken, pharmacyController.restockMedicine);

// Low-Stock Alerts List (PB07 / PB12)
router.get('/alerts', verifyToken, pharmacyController.getLowStockAlerts);

// Prescription Queue & Dispensing (PB13)
router.get('/prescriptions', verifyToken, pharmacyController.getPrescriptionsQueue);
router.post('/prescriptions/:prescriptionId/dispense-item/:itemId', verifyToken, pharmacyController.dispensePrescriptionItem);
router.post('/prescriptions/:prescriptionId/dispense', verifyToken, pharmacyController.dispenseEntirePrescription);

module.exports = router;
