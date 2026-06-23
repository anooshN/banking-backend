# 1. What Is This App?

## The Simple Version (5-year-old explanation)

Imagine a piggy bank. You put money in, take money out, and check how much you have.

Now imagine that piggy bank is on the internet, millions of people use it, it never sleeps, never makes mistakes, and can handle thousands of people using it at the exact same second.

That is this app. It is a **banking application** — the kind of software that powers real banks.

---

## What Can It Do?

### For a Customer
- **Create an account** — like opening a checking or savings account at a bank
- **See your balance** — how much money you have right now
- **Send money** — transfer money to another person, even in another country
- **Pay bills** — pay rent, utilities, subscriptions
- **Get a loan** — borrow money and pay it back monthly (with interest)
- **Manage cards** — see your debit card, block it if lost
- **Get notified** — receive alerts when money goes in or out
- **Download statements** — get a PDF of all your transactions

### For a Bank Employee (Teller / Admin)
- **Open accounts for customers**
- **Freeze suspicious accounts**
- **Review fraud alerts**
- **Issue cards**
- **Approve loans**
- **View immutable audit logs** — a permanent record of every action ever taken

---

## The Scale

This app is built to handle:
- **Millions of users**
- **Thousands of transactions per second**
- **Zero downtime** — it stays running even when parts of it break
- **Data that can never be lost** — your money records are replicated and backed up

---

## What Makes It Different From a Simple CRUD App?

A simple app is like a notepad. You write things, read things, change things, delete things.

A banking app has additional requirements:

**1. Consistency** — If you send $100 to a friend, that $100 must leave your account AND arrive in their account. It cannot disappear in the middle.

**2. Auditability** — Every single action must be permanently recorded. Who did what, when, from where.

**3. Security** — Only you can see your account. Nobody else. Not even a bank employee without proper authorization.

**4. Reliability** — The app must work 99.99% of the time. That is less than 1 hour of downtime per year.

**5. Fraud Detection** — The system watches for suspicious patterns and blocks them automatically.
