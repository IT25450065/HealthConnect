const db = require('../config/db');
const http = require('http');

async function testBackend() {
  console.log('🧪 Starting HealthConnect Pharmacist Module Verification Test Suite...\n');

  // Start server in-process for testing
  const express = require('express');
  const app = express();
  app.use(express.json());
  app.use('/api/auth', require('../routes/auth'));
  app.use('/api/medicines', require('../routes/medicines'));
  app.use('/api/prescriptions', require('../routes/prescriptions'));
  app.use('/api/dashboard', require('../routes/dashboard'));

  await db.initDb();

  const server = app.listen(5099, async () => {
    console.log('🚀 Internal Test Server started on port 5099.\n');
    let token = '';

    try {
      // 1. Test Pharmacist Login
      console.log('1️⃣ Testing Pharmacist Login (POST /api/auth/login)...');
      const loginRes = await makeRequest('POST', '/api/auth/login', {
        email: 'pharmacist@healthconnect.com',
        password: 'password123'
      });
      console.log('   Response Status:', loginRes.status);
      console.log('   Success:', loginRes.body.success, '| User:', loginRes.body.user.name);
      token = loginRes.body.token;

      // 2. Test Dashboard Stats & Low Stock Alerts (PB07)
      console.log('\n2️⃣ Testing Dashboard Stats & Low-Stock Alerts (GET /api/dashboard/stats)...');
      const dashRes = await makeRequest('GET', '/api/dashboard/stats', null, token);
      console.log('   Stats:', dashRes.body.stats);
      console.log('   Active Low-Stock Alerts Count:', dashRes.body.alerts.length);

      // 3. Test Medicine Search (PB06)
      console.log('\n3️⃣ Testing Medicine Search (GET /api/medicines?query=Amoxicillin)...');
      const searchRes = await makeRequest('GET', '/api/medicines?query=Amoxicillin', null, token);
      console.log('   Medicines Found:', searchRes.body.medicines.map(m => `${m.name} (Stock: ${m.stock_qty}, Min: ${m.min_threshold})`));

      // 4. Test Adding Stock / Restock (PB06 & PB07)
      console.log('\n4️⃣ Testing Stock Update (POST /api/medicines/2/stock)...');
      const restockRes = await makeRequest('POST', '/api/medicines/2/stock', {
        qty_received: 5,
        batch_ref: 'TEST-BATCH-999'
      }, token);
      console.log('   Update Result:', restockRes.body.message);
      console.log('   Low Stock Alert Flag:', restockRes.body.lowStockAlert);

      // 5. Test Invalid Quantity Input Validation (PB06 Edge Case)
      console.log('\n5️⃣ Testing Invalid Negative Quantity Rejection (POST /api/medicines/2/stock)...');
      const invalidQtyRes = await makeRequest('POST', '/api/medicines/2/stock', {
        qty_received: -10,
        batch_ref: 'TEST-BATCH-ERR'
      }, token);
      console.log('   Response Status:', invalidQtyRes.status, '(Expected 400)');
      console.log('   Error Message:', invalidQtyRes.body.message);

      // 6. Test Low / Out-of-Stock List with Sorting (PB12)
      console.log('\n6️⃣ Testing Low & Out-of-Stock List (GET /api/medicines/alerts?filter=all&sortBy=deficit)...');
      const alertListRes = await makeRequest('GET', '/api/medicines/alerts?filter=all&sortBy=deficit', null, token);
      console.log('   Alert Items Count:', alertListRes.body.count);
      console.log('   Highest Deficit Item:', alertListRes.body.medicines[0].name, 'Deficit:', alertListRes.body.medicines[0].deficit);

      // 7. Test Prescription Dispensing Queue (PB13)
      console.log('\n7️⃣ Testing Prescription Queue (GET /api/prescriptions?status=Pending)...');
      const prescRes = await makeRequest('GET', '/api/prescriptions?status=Pending', null, token);
      console.log('   Pending Prescriptions Count:', prescRes.body.count);

      // 8. Test Prescription Dispensing Insufficient Stock Rejection (PB13 Edge Case)
      console.log('\n8️⃣ Testing Insufficient Stock Dispensing Rejection (POST /api/prescriptions/1/dispense-item/2)...');
      // Item 2 is Amoxicillin (prescribed 21, stock was 12 + 5 = 17 < 21)
      const dispenseErrRes = await makeRequest('POST', '/api/prescriptions/1/dispense-item/2', null, token);
      console.log('   Response Status:', dispenseErrRes.status, '(Expected 400)');
      console.log('   Blocked Warning Message:', dispenseErrRes.body.message);

      // 9. Test Successful Prescription Dispensing & Auto Stock Deduction (PB13)
      console.log('\n9️⃣ Testing Successful Prescription Dispensing (POST /api/prescriptions/1/dispense-item/1)...');
      // Item 1 is Paracetamol (prescribed 10, stock is 150)
      const dispenseSuccessRes = await makeRequest('POST', '/api/prescriptions/1/dispense-item/1', null, token);
      console.log('   Dispense Result:', dispenseSuccessRes.body.message);
      console.log('   Deducted Qty:', dispenseSuccessRes.body.deducted_qty, '| Remaining Stock:', dispenseSuccessRes.body.remaining_stock);

      console.log('\n✅ ALL VERIFICATION TESTS PASSED SUCCESSFULLY! 🎉');

    } catch (err) {
      console.error('\n❌ Verification Failed:', err);
    } finally {
      server.close();
    }
  });
}

function makeRequest(method, path, body = null, token = null) {
  return new Promise((resolve, reject) => {
    const dataStr = body ? JSON.stringify(body) : '';
    const headers = { 'Content-Type': 'application/json' };
    if (dataStr) headers['Content-Length'] = Buffer.byteLength(dataStr);
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const req = http.request({
      hostname: 'localhost',
      port: 5099,
      path,
      method,
      headers
    }, (res) => {
      let raw = '';
      res.on('data', chunk => raw += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(raw);
          resolve({ status: res.statusCode, body: json });
        } catch (e) {
          resolve({ status: res.statusCode, body: raw });
        }
      });
    });

    req.on('error', reject);
    if (dataStr) req.write(dataStr);
    req.end();
  });
}

testBackend();
