# MySQL migration

The app now uses MySQL by default. Its original JSON data is kept in
`inventory_data.json` as a one-time import source and backup.

1. Copy `backend/.env.example` to `backend/.env` and enter the MySQL credentials.
2. From the `backend` folder run `npm run db:migrate`.
3. Start the app with `npm start`.

The migration creates the database and the five related tables: pharmacists,
medicines, prescriptions, prescription_items, and stock_transactions. It imports
the JSON data only when the MySQL database is empty, and never drops or overwrites
existing MySQL records.
