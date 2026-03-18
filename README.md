# Fraud Shield

**Fraud Shield** is a native Android application that helps detect **financial fraud messages in real time**.  
It analyzes incoming SMS and supported messaging notifications, identifies suspicious content such as phishing links, fake KYC requests, OTP scams, and fake utility-payment alerts, and warns the user instantly.

---

## Overview

Mobile fraud is growing rapidly through SMS and messaging apps. Fraudsters often impersonate banks, electricity boards, gas agencies, water services, and payment platforms to create urgency and trick users into clicking malicious links, sharing sensitive information, or making fake payments.

Fraud Shield is designed to reduce this risk by providing **real-time, on-device fraud detection** directly on the user's phone.

---

## Key Features

- Real-time SMS fraud detection
- Notification-based fraud detection for:
  - Google Messages / RCS
  - WhatsApp
  - Gmail
- Detection of common scam patterns such as:
  - Fake KYC update requests
  - OTP scams
  - Banking fraud
  - Utility disconnection fraud
  - Lottery / reward scams
- Suspicious link scanning
- Instant floating fraud alert overlay
- Fraud score, risk level, and scam category
- Detection reasons for transparency
- Local scan history storage
- Privacy-friendly on-device processing

---

## How It Works

1. A new SMS or supported notification arrives  
2. The app captures the visible message content  
3. The fraud engine analyzes the content in real time  
4. It checks for:
   - suspicious keywords
   - scam phrases
   - phishing links
   - OTP misuse
   - urgency-based fraud patterns  
5. A fraud score and risk category are generated  
6. If the message is risky, the app shows an instant warning  
7. The result is stored in local history for review  

---

## Tech Stack

- **Kotlin**
- **Android Studio**
- **Jetpack Compose**
- **NotificationListenerService**
- **SharedPreferences / Local Storage**
- **Rule-based fraud detection engine**
- **NLP-style text analysis**
- **TensorFlow Lite-ready architecture** for future on-device AI model deployment

---

## Project Goal

The goal of Fraud Shield is to build a **privacy-preserving, real-time financial fraud detection system** that can operate directly on mobile devices and help users identify threats before they take harmful action.

---

## Current Status

The current prototype includes:

- SMS fraud detection
- Notification-based detection for supported apps
- Fraud scoring and categorization
- Overlay alert system
- Local scan history
- Hybrid rule-based and lightweight AI-assisted scoring

Future versions will include a trained on-device model using **TensorFlow Lite** for stronger local inference.

---

## Future Scope

- Full TensorFlow Lite text classification model
- Multilingual fraud detection
- Stronger malicious URL reputation checks
- Broader support for additional messaging platforms
- Improved fraud classification using trained datasets
- Enhanced UI and analytics dashboard

---

## Screenshots

_Add your screenshots here after uploading them to the repository._

Example:

```md
![Home Screen](screenshots/home.png)
![Fraud Alert](screenshots/alert.png)
![History Screen](screenshots/history.png)
