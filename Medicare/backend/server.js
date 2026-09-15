require('dotenv').config();
const app = require('./app');
const db = require('./config/db');

const PORT = process.env.PORT || 5000;

// Start Server after initializing Database connection
async function startServer() {
  try {
    await db.initDb();
    app.listen(PORT, () => {
      console.log(`==================================================`);
      console.log(`🏥 HealthConnect Pharmacist & Team Module Server Running`);
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
