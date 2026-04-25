-- ══════════════════════════════════════════════════════════════
-- Forex Portfolio Management — Database Schema
-- Run this in MySQL to set up the database and table.
-- ══════════════════════════════════════════════════════════════

-- Create database
CREATE DATABASE IF NOT EXISTS forex;
USE forex;

-- Create trades table
CREATE TABLE IF NOT EXISTS trades (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    type        VARCHAR(10)  NOT NULL COMMENT 'BUY or SELL',
    instrument  VARCHAR(50)  NOT NULL COMMENT 'e.g. USD/INR, GOLD, NIFTY 50',
    quantity    DOUBLE       NOT NULL COMMENT 'Number of units',
    price       DOUBLE       NOT NULL COMMENT 'Price per unit',
    timestamp   VARCHAR(30)  DEFAULT (NOW()) COMMENT 'Trade creation time'
);

-- Optional: Insert sample data for testing
INSERT INTO trades (type, instrument, quantity, price, timestamp) VALUES
    ('BUY',  'USD/INR',   100,   83.50,  '2025-01-10 09:30:00'),
    ('BUY',  'GOLD',      5,     2350.00,'2025-01-12 10:15:00'),
    ('SELL', 'EUR/USD',    200,   1.085,  '2025-01-15 14:00:00'),
    ('BUY',  'NIFTY 50',  50,    22150.0,'2025-01-20 09:45:00'),
    ('SELL', 'USD/INR',   100,   84.20,  '2025-02-01 11:30:00');
