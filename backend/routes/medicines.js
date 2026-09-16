const express = require('express');
const router = express.Router();
const { verifyToken } = require('../middleware/auth');
const pharmacyController = require('../controllers/pharmacyController');

// Medicines Inventory Routes (Backward Compatible Alias)
router.get('/', verifyToken, pharmacyController.getAllMedicines);
router.post('/', verifyToken, pharmacyController.addMedicine);
router.post('/:id/stock', verifyToken, pharmacyController.restockMedicine);
router.get('/alerts', verifyToken, pharmacyController.getLowStockAlerts);

module.exports = router;
