const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const db = require('../config/db');

const JWT_SECRET = process.env.JWT_SECRET || 'healthconnect_pharmacist_super_secret_key_2026';

exports.login = async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ success: false, message: 'Email and password are required.' });
    }

    const users = await db.query('SELECT * FROM pharmacists WHERE email = ?', [email.trim()]);
    if (users.length === 0) {
      return res.status(401).json({ success: false, message: 'Invalid credentials. Pharmacist account not found.' });
    }

    const user = users[0];
    const isMatch = await bcrypt.compare(password, user.password_hash);

    if (!isMatch) {
      return res.status(401).json({ success: false, message: 'Invalid email or password.' });
    }

    const token = jwt.sign(
      { id: user.id, email: user.email, name: user.name, role: 'pharmacist' },
      JWT_SECRET,
      { expiresIn: '12h' }
    );

    res.json({
      success: true,
      message: 'Authentication successful.',
      token,
      user: {
        id: user.id,
        name: user.name,
        email: user.email,
        role: 'pharmacist'
      }
    });
  } catch (err) {
    console.error('Login Error:', err);
    res.status(500).json({ success: false, message: 'Server error during login authentication.' });
  }
};

exports.getMe = async (req, res) => {
  try {
    const users = await db.query('SELECT id, name, email, created_at FROM pharmacists WHERE id = ?', [req.user.id]);
    if (users.length === 0) {
      return res.status(404).json({ success: false, message: 'User profile not found.' });
    }

    res.json({
      success: true,
      user: {
        ...users[0],
        role: 'pharmacist'
      }
    });
  } catch (err) {
    console.error('Session Verification Error:', err);
    res.status(500).json({ success: false, message: 'Server error verifying session.' });
  }
};

exports.changePassword = async (req, res) => {
  try {
    const { currentPassword, newPassword, confirmPassword } = req.body;
    const userId = req.user.id;

    if (!currentPassword || !newPassword || !confirmPassword) {
      return res.status(400).json({ success: false, message: 'Current password, new password, and confirm password are required.' });
    }

    if (newPassword !== confirmPassword) {
      return res.status(400).json({ success: false, message: 'New password and confirm password do not match.' });
    }

    if (newPassword.length < 6) {
      return res.status(400).json({ success: false, message: 'New password must be at least 6 characters long.' });
    }

    const users = await db.query('SELECT * FROM pharmacists WHERE id = ?', [userId]);
    if (users.length === 0) {
      return res.status(404).json({ success: false, message: 'Pharmacist account not found.' });
    }

    const user = users[0];
    const isMatch = await bcrypt.compare(currentPassword, user.password_hash);
    if (!isMatch) {
      return res.status(401).json({ success: false, message: 'Incorrect current password.' });
    }

    const newHash = await bcrypt.hash(newPassword, 10);
    await db.query('UPDATE pharmacists SET password_hash = ? WHERE id = ?', [newHash, userId]);

    res.json({
      success: true,
      message: 'Password changed successfully! Please use your new password next time you log in.'
    });
  } catch (err) {
    console.error('Change Password Error:', err);
    res.status(500).json({ success: false, message: 'Server error updating password.' });
  }
};

