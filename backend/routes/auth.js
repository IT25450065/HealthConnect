const express = require('express');
const router = express.Router();
const { verifyToken } = require('../middleware/auth');
const authController = require('../controllers/authController');

// Auth Endpoints
router.post('/login', authController.login);
router.get('/me', verifyToken, authController.getMe);
router.post('/change-password', verifyToken, authController.changePassword);
router.put('/profile', verifyToken, authController.updateProfile);

module.exports = router;
