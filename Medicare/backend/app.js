const express = require('express');
const cors = require('cors');
const path = require('path');
require('dotenv').config();

// Pharmacy Module Route Imports (Ahamed's Module)
const authRoutes = require('./routes/auth');
const pharmacyRoutes = require('./routes/pharmacy');

// Backward Compatibility Aliases for Pharmacy Module
const medicinesRoutes = require('./routes/medicines');
const prescriptionsRoutes = require('./routes/prescriptions');
const dashboardRoutes = require('./routes/dashboard');

const app = express();

// Global Middlewares
app.use(cors());
app.use(express.json());

// Serve Static Frontend Assets
const frontendPath = path.join(__dirname, '..', 'frontend');
app.use(express.static(frontendPath));

// HealthConnect Pharmacy API Routes
app.use('/api/auth', authRoutes);
app.use('/api/pharmacy', pharmacyRoutes);

// Backward Compatibility Aliases for Pharmacy Module
app.use('/api/medicines', medicinesRoutes);
app.use('/api/prescriptions', prescriptionsRoutes);
app.use('/api/dashboard', dashboardRoutes);

// Fallback SPA Route Handler
app.get('*', (req, res) => {
  if (req.path.startsWith('/api')) {
    return res.status(404).json({ success: false, message: 'API Endpoint Not Found' });
  }
  res.sendFile(path.join(frontendPath, 'index.html'));
});

// Global Error Handler Middleware
app.use((err, req, res, next) => {
  console.error('Unhandled HealthConnect Server Error:', err.stack);
  res.status(500).json({
    success: false,
    message: 'Internal Server Error',
    error: process.env.NODE_ENV === 'development' ? err.message : undefined
  });
});

module.exports = app;
