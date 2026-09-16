# 🏥 HealthConnect — Pharmacy Module

Pharmacy Inventory & Prescription Dispensing Module for HealthConnect.

---

## 🏗️ Module Architecture

```
HealthConnect/
├── backend/
│   ├── config/
│   │   └── db.js                  ← Shared MySQL database connection
│   ├── middleware/
│   │   └── auth.js                ← Shared login/role-check logic
│   ├── routes/
│   │   ├── pharmacy.js            ← Pharmacy module routes
│   │   └── auth.js                ← Shared authentication routes
│   ├── controllers/
│   │   ├── pharmacyController.js  ← Pharmacy business logic (Ahamed's Module)
│   │   └── authController.js      ← Authentication business logic
│   ├── app.js                     ← Express app & route wiring
│   └── server.js                  ← Starts backend server
│
├── frontend/
│   ├── src/
│   │   ├── pages/
│   │   │   └── pharmacy/          ← Stock management, alerts list, dispensing queue
│   │   ├── components/            ← Reusable UI components
│   │   └── App.js                 ← Router entry module
│   ├── css/
│   │   └── styles.css             ← Apple WWDC25 Liquid Glass & Box Grid System
│   ├── js/
│   │   └── app.js                 ← Single-Page App controller
│   ├── index.html
│   └── package.json
│
├── database/
│   └── schema.sql                 ← Shared SQL schema file
│
├── .env.example                   ← Shared config template
├── .gitignore                     ← Git ignore rules
└── README.md
```

---

## ⚡ Quick Start Guide

### 1. Database Setup
Import the database schema into MySQL:
```bash
mysql -u root -p < database/schema.sql
```

### 2. Environment Configuration
Copy `.env.example` to `.env` inside `backend/`:
```bash
cp .env.example backend/.env
```

### 3. Start Backend Server
```bash
cd backend
npm install
node server.js
```
Open **[http://localhost:5000](http://localhost:5000)** in your browser.

---

## 🔑 Demo Credentials

- **Email:** `pharmacist@healthconnect.com`
- **Password:** `password123`

---

## 💊 Pharmacy Module API Endpoints (`/api/pharmacy`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/auth/login` | Pharmacist login & JWT generation |
| `GET` | `/api/pharmacy/dashboard/stats` | Real-time inventory metrics & active alert counts |
| `GET` | `/api/pharmacy/medicines` | Retrieve/search all registered medicines |
| `POST` | `/api/pharmacy/medicines` | Register a new medicine record (PB06) |
| `POST` | `/api/pharmacy/medicines/:id/stock` | Record stock delivery batch addition (PB06) |
| `GET` | `/api/pharmacy/alerts` | Filtered low-stock & out-of-stock list (PB07/PB12) |
| `GET` | `/api/pharmacy/prescriptions` | Doctor-issued prescription queue (PB13) |
| `POST` | `/api/pharmacy/prescriptions/:pId/dispense-item/:itemId` | Dispense single line item & deduct inventory |
| `POST` | `/api/pharmacy/prescriptions/:pId/dispense` | Dispense all line items for prescription |
