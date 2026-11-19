# ARUSHI Growth Hub setup

This branch adds three app surfaces:

- Social account connections and account analytics through the official YouTube and Instagram APIs.
- Daily topic-based Google News RSS briefings scheduled with Android WorkManager.
- An encrypted local shopping list, product-search hand-off and HTTPS merchant checkout hand-off. ARUSHI never stores payment-card data or submits an order.

## 1. Social API deployment (required)

The Android app deliberately contains no provider client secrets. The Firebase HTTPS function `socialApi` performs OAuth code exchange and stores provider tokens encrypted server-side in Firestore.

Before connecting accounts:

1. In Google Cloud, create an OAuth **Web application** client for the same Firebase project. Enable YouTube Data API v3 and YouTube Analytics API. Add this exact authorized redirect URI (replace the project ID):
   `https://us-central1-PROJECT_ID.cloudfunctions.net/socialApi`
2. In Meta for Developers, configure Instagram Login for a professional/creator account and add the same callback URL as its redirect URI. Request `instagram_business_basic` and `instagram_business_manage_insights` access through the normal Meta review process where required.
3. Set Firebase Functions secrets from a trusted terminal. Never put values in Git, the APK, screenshots or this chat:

   ```sh
   firebase functions:secrets:set ARUSHI_GOOGLE_CLIENT_ID
   firebase functions:secrets:set ARUSHI_GOOGLE_CLIENT_SECRET
   firebase functions:secrets:set ARUSHI_META_APP_ID
   firebase functions:secrets:set ARUSHI_META_APP_SECRET
   ```

4. Generate a random 32-byte encryption key locally and store it as a Firebase secret. Example with Node.js:
   ```sh
   node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
   firebase functions:secrets:set ARUSHI_SOCIAL_ENCRYPTION_KEY
   ```
   Paste the generated value only into the Firebase secret prompt. Keep a secure backup; losing this key makes existing encrypted provider tokens unreadable.
5. Deploy only the function:
   ```sh
   cd functions
   npm install
   cd ..
   firebase deploy --only functions:socialApi
   ```

Users must sign in to ARUSHI Connect before starting OAuth. Provider approval, API access level and review status are controlled by Google/Meta. Analytics are only available for accounts the signed-in user is authorized to manage. The app requests read-only YouTube scopes and the minimum Instagram professional-account scopes used by the insights API.

The function encrypts access and refresh tokens with AES-256-GCM before writing them to Firestore. Firebase Authentication is checked on start, analytics and disconnect calls. OAuth state is random, short-lived and single-use. Disconnect deletes ARUSHI's stored token records; users should also revoke the app from the provider's own account settings.

## 2. News briefings

Open **Command Center → ARUSHI Growth Hub**. Set a topic and local delivery time, allow Android notifications, then enable the daily briefing. WorkManager is periodic and battery-aware; Android may defer the run. This is a daily digest of up to five Google News RSS headlines, not a guaranteed exact-time alarm.

## 3. Shopping

Product search opens a general Google Shopping search in the browser. Shopping-list entries are encrypted on-device. Opening a merchant link requires HTTPS and launches the merchant's own page; payment, address entry and order submission remain outside ARUSHI. A parent/guardian should approve purchases. No card numbers, passwords or payment credentials are collected by ARUSHI.

## 4. Validation notes

A successful Android APK build does not prove that provider OAuth is configured. Social connection requires the Firebase secrets, Google/Meta app configuration, provider review/permissions and deployed Cloud Function. News delivery requires notification permission and network availability. Merchant checkout depends on the merchant URL and its own account/payment rules.
