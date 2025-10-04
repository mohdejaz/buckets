# Buckets App

This is simple buckets application developed using Java Swing. It uses H2 database to store data.
It has 4 main entities - 
- Users
- Accounts: Accounts belong to a User.
- Buckets: Buckets belong to an Account.
- Transactions: Transactions belong to a Bucket.

## Screens
### Menu
![MENU](./imgs/menu.png)

It has sub menu items as Buckets, Transactions and Calculator. 

### Buckets
![BUCKETS](./imgs/buckets2.png)

It has buttons to create new bucket, edit a bucket or refill it.

### Transactions
![TRANSACTIONS](./imgs/transactions2.png)

It has buttons for multiple options. You can do following -
- Create new transaction
- Edit an existing transaction
- Delete
- Post (this is when tx is showing as pending on bank a/c)
- UnPost (reverse of above)
- Refresh screen
- Export selected transactions to CSV file
- Filter transactions using sql where clause; use column headers as column names. For example amount > 100

## Notes
1. This project uses JDatePicker available at https://github.com/JDatePicker/JDatePicker
2. To run app use run.bat
3. Initial setup: Create buckets with name and budget. Refill factor is 0.5, so it means you can refill twice a month.
4. Use app.ddl to create tables & add 1 user and 1 account. User id should be 1.

