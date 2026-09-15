const express = require('express');
const router = express.Router();
const { verifyToken } = require('../middleware/auth');
const pharmacyController = require('../controllers/pharmacyController');

// Prescriptions Queue Routes (Backward Compatible Alias)
router.get('/', verifyToken, pharmacyController.getPrescriptionsQueue);
router.post('/:prescriptionId/dispense-item/:itemId', verifyToken, pharmacyController.dispensePrescriptionItem);
router.post('/:prescriptionId/dispense', verifyToken, pharmacyController.dispenseEntirePrescription);

module.exports = router;
