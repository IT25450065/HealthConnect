const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const db = require('../config/db');
const { verifyToken, JWT_SECRET } = require('../middleware/auth');

/**
 * POST /api/auth/login
 * Pharmacist Login
 */
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({
        success: false,
        message: 'Please provide both email address and password.'
      });
    }

    // Fetch pharmacist by email
    const users = await db.query('SELECT * FROM pharmacists WHERE email = ?', [email.trim().toLowerCase()]);
    
    if (users.length === 0) {
      return res.status(401).json({
        success: false,
        message: 'Invalid credentials. No pharmacist account found with this email.'
      });
    }

    const pharmacist = users[0];

    // Check password
    const isMatch = await bcrypt.compare(password, pharmacist.password_hash);
    if (!isMatch) {
      return res.status(401).json({
        success: false,
        message: 'Invalid credentials. Incorrect password.'
      });
    }

    // Generate JWT Token
    const payload = {
      id: pharmacist.id,
      name: pharmacist.name,
      email: pharmacist.email,
      role: 'Pharmacist'
    };

    const token = jwt.sign(payload, JWT_SECRET, { expiresIn: '12h' });

    return res.json({
      success: true,
      message: 'Login successful.',
      token,
      user: {
        id: pharmacist.id,
        name: pharmacist.name,
        email: pharmacist.email,
        role: 'Pharmacist'
      }
    });
  } catch (err) {
    console.error('Login error:', err);
    return res.status(500).json({
      success: false,
      message: 'Server error during authentication processing.'
    });
  }
});

/**
 * GET /api/auth/me
 * Validate current user session
 */
router.get('/me', verifyToken, async (req, res) => {
  try {
    const users = await db.query('SELECT id, name, email, created_at FROM pharmacists WHERE id = ?', [req.user.id]);
    if (users.length === 0) {
      return res.status(404).json({ success: false, message: 'Pharmacist account not found.' });
    }
    return res.json({
      success: true,
      user: {
        ...users[0],
        role: 'Pharmacist'
      }
    });
  } catch (err) {
    return res.status(500).json({ success: false, message: 'Server error verifying session.' });
  }
});

module.exports = router;
