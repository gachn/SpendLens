# Play Store Screenshots Guide

## Overview
SpendVault now includes a demo mode specifically designed for creating Play Store screenshots without exposing real financial data.

## How to Use Demo Mode

### Option 1: Quick Demo Mode (Recommended)

**Step 1: Add a debug menu item**
```kotlin
// In MainActivity.kt, add this to your debug menu:
fun showDemoMode() {
    setContent {
        DemoModeScreen()
    }
}
```

**Step 2: Take screenshots**
1. Run the app in debug mode
2. Navigate to demo mode (you'll see "DEMO MODE" badge)
3. Switch between screens: Dashboard, Analytics, Transactions, Accounts
4. Take screenshots using Android screenshot tools

### Option 2: Build a dedicated screenshots APK

**Step 1: Create a new build variant**
Add this to your `app/build.gradle.kts`:
```kotlin
buildTypes {
    screenshots {
        initWith(getByName("debug"))
        applicationIdSuffix = ".screenshots"
        versionNameSuffix = "-screenshots"
        manifestPlaceholders["app_name"] = "SpendVault Demo"
    }
}
```

**Step 2: Set default to demo mode**
Create `app/src/screenshots/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="SpendVault Demo">
        <activity android:name=".MainActivity"
                  android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Step 3: Force demo mode on startup**
```kotlin
// In MainActivity.kt onCreate:
if (BuildConfig.BUILD_TYPE == "screenshots") {
    setContent {
        DemoModeScreen()
    }
    return // Skip normal app initialization
}
```

## Screenshot Guidelines

### Required Screenshots
1. **Dashboard** - Shows monthly spending overview
2. **Analytics** - Category breakdown and trends  
3. **Transactions** - Recent transaction list
4. **Accounts** - Account balances and bills

### Best Practices
- Use **7-inch tablet** or **10-inch tablet** frame for best visibility
- Take screenshots in both **light and dark** themes
- Ensure text is readable (12px minimum)
- Show variety: different categories, transaction types
- Keep device status bar clean (time, battery, notifications)

### Screenshot Tools
- **Android Studio**: Use Layout Inspector for pixel-perfect screenshots
- **ADB Command:** `adb shell screencap -p /sdcard/screenshot.png`
- **Screenshot Maker Pro**: Professional screenshot framing tool
- **Device Frames.io**: Create beautiful device mockups

## Demo Data Features

### Realistic Fake Data
- ✅ Realistic merchant names (Netflix, Amazon, Swiggy, etc.)
- ✅ Realistic amounts and categories
- ✅ Proper transaction dates (last 30 days)
- ✅ Indian currency format (₹)
- ✅ Account types (Savings, Credit Card)
- ✅ Bills and subscriptions

### Data Categories Included
- **Transactions**: 15 realistic transactions across categories
- **Categories**: 10 categories with budgets and icons
- **Budgets**: 3 active budgets with alert thresholds
- **Accounts**: 2 accounts (Savings + Credit Card)
- **Bills**: 3 recurring bills with due dates
- **SMS**: Sample SMS messages for parsing demo

### Total Monthly Demo Summary
- **Income**: ₹6,240 (Salary)
- **Expenses**: ₹14,860 (Realistic spending)
- **Net Balance**: ₹1,24,500 (in savings account)
- **Categories**: Food & Dining, Shopping, Bills, Transport, etc.

## Privacy & Security

### Why This Approach?
- ✅ **Zero real data exposure** - All data is completely fake
- ✅ **GDPR compliant** - No personal financial information
- ✅ **Professional appearance** - Looks like real app usage
- ✅ **Consistent branding** - SpendVault logo and colors throughout
- ✅ **Easy to update** - Change demo data anytime

### What's NOT Included
- ❌ Real transaction amounts
- ❌ Real merchant names  
- ❌ Real account numbers
- ❌ Real bank names
- ❌ Personal SMS content
- ❌ Any identifiable information

## Customizing Demo Data

To modify the fake data, edit `DemoDataGenerator.kt`:

```kotlin
// Change amounts
TransactionEntity(
    amountMinor = 5000000L, // ₹5,000 instead of ₹2,450
    // ...
)

// Change merchants
TransactionEntity(
    merchant = "Your Brand",
    // ...
)

// Add more transactions
TransactionEntity(
    id = 16,
    merchant = "New Merchant",
    // ...
)
```

## Building Final Screenshots

### Recommended Tools
1. **Device Frames.io** - Professional device mockups
2. **Screenshot Maker Pro** - Perfect Play Store frames
3. **Canva** - Custom graphics and overlays
4. **Figma** - Design-aligned screenshots

### Final Checklist
- [ ] All 4+ screenshots taken
- [ ] Device frames added
- [ ] Both light and dark themes
- [ ] Text is readable and clear
- [ ] SpendVault branding consistent
- [ ] No real data visible
- [ ] Professional appearance

## Troubleshooting

**Demo mode not showing:**
- Ensure `DemoModeScreen()` is being called
- Check build type is correct
- Verify dependencies are resolved

**Screenshots look blurry:**
- Use device frame tools instead of direct screenshots
- Set device display to highest resolution
- Take screenshots on high-density displays

**Data not realistic enough:**
- Modify `DemoDataGenerator.kt` values
- Add more variety to transactions
- Update category budgets to match spending

## Next Steps

1. ✅ Demo data generator created
2. ✅ Demo mode screen built
3. ⏳ Add demo mode toggle to settings
4. ⏳ Build screenshots APK
5. ⏳ Take final screenshots
6. ⏳ Upload to Play Console

---

**Result:** Professional Play Store screenshots with zero risk of exposing real financial data!