const express = require('express');
const cors = require('cors');
const path = require('path');
require('dotenv').config();

const db = require('./config/db');
const authRoutes = require('./routes/auth');
const medicinesRoutes = require('./routes/medicines');
const prescriptionsRoutes = require('./routes/prescriptions');
const dashboardRoutes = require('./routes/dashboard');

const app = express();
const PORT = process.env.PORT || 5000;

// Middleware
app.use(cors());
app.use(express.json());

// Serve frontend static files
const frontendPath = path.join(__dirname, '..', 'frontend');
app.use(express.static(frontendPath));

// API Routes
app.use('/api/auth', authRoutes);
app.use('/api/medicines', medicinesRoutes);
app.use('/api/prescriptions', prescriptionsRoutes);
app.use('/api/dashboard', dashboardRoutes);

// Fallback to index.html for SPA routing
app.get('*', (req, res) => {
  if (req.path.startsWith('/api')) {
    return res.status(404).json({ success: false, message: 'API Endpoint Not Found' });
  }
  res.sendFile(path.join(frontendPath, 'index.html'));
});

// Global Error Handler
app.use((err, req, res, next) => {
  console.error('Unhandled Server Error:', err.stack);
  res.status(500).json({
    success: false,
    message: 'Internal Server Error',
    error: process.env.NODE_ENV === 'development' ? err.message : undefined
  });
});

// Start Server after initializing DB connection
async function startServer() {
  try {
    await db.initDb();
    app.listen(PORT, () => {
      console.log(`==================================================`);
      console.log(`🏥 HealthConnect Pharmacist Module Server Running`);
      console.log(`🌐 URL: http://localhost:${PORT}`);
      console.log(`🗄️ Database Engine: ${db.getDriver().toUpperCase()}`);
      console.log(`==================================================`);
    });
  } catch (err) {
    console.error('❌ Failed to launch HealthConnect server:', err);
    process.exit(1);
  }
}

startServer();
