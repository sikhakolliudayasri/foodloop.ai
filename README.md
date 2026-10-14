## FoodLoop AI

FoodLoop AI coordinates verified surplus food with recipient demand. This repository contains an Android application in `app/`, a React/Vite frontend in `src/`, and a FastAPI/SQLite backend in `backend/`.

### Run the web application

Prerequisites: Node.js 20+, Python 3.11+, and npm.

1. Install frontend packages:
	```powershell
	npm install
	```

2. Create a Python environment and install the API dependencies:
	```powershell
	py -3.13 -m venv .venv
	.\.venv\Scripts\Activate.ps1
	py -m pip install -r backend/requirements.txt
	```

3. In one terminal, start the FastAPI backend from the repository root:
	```powershell
	py -m uvicorn backend.app.main:app --reload --port 8000
	```
	*On initial startup, the database `foodloop.db` is initialized and pre-seeded with realistic Restaurant Surplus meals and D-Mart Near-Expiry items ready for instant exploration.*

4. In a second terminal, start the React frontend:
	```powershell
	npm run dev
	```
	Open `http://localhost:5173` in your browser.

### Key Features & User Flow

1. **Sign-In / Sign-Up & 1-Click Demo Profiles**:
   - Clean, friendly auth interface with password reveal toggle and role selection.
   - 1-click instant login presets for testing:
     - 🍲 **Paradise Biryani** (*Restaurant Donor*)
     - 🏬 **D-Mart Supermarket** (*Near-Expiry Grocery Donor*)
     - 🏠 **Hope Foundation** (*Recipient Organization / Shelter*)
     - 🚚 **City Coordinator** (*AI Dispatch & Logistics Desk*)

2. **Public Live Food Hub (Explore Feed)**:
   - **🍲 Restaurants & Food Stalls**: Cooked meals, buffet surplus, biryani portions with real-time countdown clocks to prevent food waste.
   - **🏬 Supermarkets & D-Marts**: Near-expiry packaged items, dairy, pulses, and dry goods posted before expiry date with MRP comparison tags (₹0 Free Community Food).
   - Real-time search by food name, store location, and category filtering (Cooked Meals, Produce, Bakery, Dairy, Packaged Goods).

3. **Direct Food Claiming with Mandatory Reason**:
   - Verified recipient organizations click **"Claim Food"** and must supply a legitimate justification (e.g. *Evening dinner for 40 children in shelter home*), specify beneficiary type, and request quantity.
   - Quick one-click reason templates for shelters, community kitchens, orphanages, and elderly care homes.

4. **Donor Review & Digital Pickup Pass**:
   - Donors receive incoming requests in their **Claims Inbox** with the recipient's reason highlighted in quote cards.
   - Donors can **Approve** or **Decline** with custom pickup instructions.
   - Approving generates an official **Digital Pickup Pass (`#PASS-...`)** with a collection code.
   - Receivers present the pass upon collection, and handoffs are tracked from pickup to delivery.

5. **AI Matching & Logistics Desk**:
   - Constraint-checked deterministic ranking between community demands and available food donations.
   - Interactive leaflet map showing live donor locations and recipient destinations.

Run backend tests:
```powershell
py -3.13 -m pytest backend/tests
```

Build the production frontend:
```powershell
npm run build
```

### Data, matching, and estimates

The matching engine filters by verified usable-until time, pickup/delivery window, requested category, quantity unit and remaining demand, declared allergens, storage capability, and an 80 km local radius. Its deterministic score considers urgency, time sensitivity, quantity fit, allocation history, and straight-line distance. Distance and travel time are estimates, not road-network measurements. No allocation is created until a coordinator sends an offer, and quantities are reserved transactionally only after recipient acceptance and coordinator approval.

Food-safety verification is supplied by the donor; the application does not certify food safety. Impact reports separate listed food from recipient-confirmed delivered food. Waste avoided, meals served, carbon savings, and route efficiency are not claimed without a documented baseline. Prediction and route optimization interfaces are not yet available in this web version.

### Android app

The Android app uses Android Studio and Gradle. Open the repository in Android Studio, sync, and run the `app` configuration on an emulator or device. The Android-specific setup steps and Gemini configuration are in the section below.

---

<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/461bbf74-b2a6-4a2c-8b49-93a1495165b8

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.
