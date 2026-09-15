const express = require('express');
const router = express.Router();
const { verifyToken } = require('../middleware/auth');
const pharmacyController = require('../controllers/pharmacyController');

// Dashboard Routes (Backward Compatible Alias)
router.get('/stats', verifyToken, pharmacyController.getDashboardStats);

module.exports = router;
