<h1 align="center">PaymentX</h1>

<p align="center">
  Closed-Loop Digital Payments Platform For VIT
</p>

<p align="center">
  <a href="https://deepwiki.com/pratham-developer/PaymentX">
    <img src="https://img.shields.io/badge/DeepWiki-Architecture_%26_System_Design-0A66C2?style=for-the-badge" alt="DeepWiki Documentation" />
  </a>
</p>

## Overview

PaymentX is a fintech platform designed to enable seamless NFC tap-and-pay transactions across VIT's campus, robust wallet management for students, and automated financial settlements for merchants.

The platform manages the complete payment lifecycle, including:

- NFC Tap-and-Pay processing with secure PIN validation
- Wallet management with instant top-ups via Cashfree Payment Gateway
- Idempotent transaction processing backed by Redis for reliable NFC payments
- Automated End-of-Day (EOD) merchant payouts and bank transfers via Cashfree Payouts
- Asynchronous, event-driven email receipts and notifications via RabbitMQ
- Comprehensive financial reporting via automated cron jobs

## Architecture & Technical Documentation

The detailed system design, architectural decisions, request flows, data modeling strategy, and scalability considerations are documented separately via DeepWiki.

<div align="center">

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/pratham-developer/PaymentX)
</div>

## Database Architecture

The following Entity Relationship Diagram represents the core data model and relationships supporting the payment lifecycle.

<p align="center">
  <img src="src/main/resources/erd.png" alt="PaymentX Entity Relationship Diagram" width="3668" />
</p>