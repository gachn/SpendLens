# Play Store Listing — Ready for Submission

## ✅ COMPLETED

### Text Content
- [x] **Short Description** (80 chars)
  ```
  SpendLens: Track expenses automatically from bank SMS. Secure, private finance tracking.
  ```
  Location: `store-listing/short-description.txt`

- [x] **Full Description** (~4000 chars)
  - Features, supported providers, permissions explained
  - Updated with your email: chauhangaurav101@gmail.com
  Location: `store-listing/full-description.txt`

- [x] **Tags/Keywords**
  ```
  expense tracker, SMS parser, personal finance, budget, transaction tracking, bank SMS, financial management, spending tracker, money manager, automatic expense tracking
  ```

### Contact Details
- [x] **Email**: chauhangaurav101@gmail.com
- [x] **Website**: https://gachn.github.io/SpendLens/
- [x] **Privacy Policy**: https://gachn.github.io/SpendLens/privacy-policy.html
- [x] **Data Deletion**: https://gachn.github.io/SpendLens/data-deletion.html

### Privacy Settings
- [x] **External Marketing**: Disabled

### Graphics (SVG Created)
- [x] **App Icon SVG** (512x512px)
  - Teal gradient (#2DD4BF → #006B5F)
  - SMS bubble with ₹ symbol
  - Modern, clean design
  Location: `store-listing/app-icon.svg`

- [x] **Feature Graphic SVG** (1024x500px)
  - Branding with SpendLens logo
  - Phone mockup showing SMS → transaction flow
  - Value prop: "No manual entry • 100% Private • Offline-first"
  Location: `store-listing/feature-graphic.svg`

### Conversion Script
- [x] **SVG to PNG converter**
  - Batch file for Windows
  - Supports ImageMagick
  - Alternative methods documented
  Location: `store-listing/convert-svg-to-png.bat`

---

## ⚠️ ACTION NEEDED — Convert Graphics

### Required for Play Store
Convert SVG files to PNG before upload:

1. **App Icon**: `app-icon.svg` → `app-icon.png` (512x512px)
2. **Feature Graphic**: `feature-graphic.svg` → `feature-graphic.png` (1024x500px)

### Conversion Methods

#### Option 1: Use the batch file (if ImageMagick installed)
```powershell
cd D:\Development\SpendLens\store-listing
.\convert-svg-to-png.bat
```

#### Option 2: Online Converters (Recommended)
- https://cloudconvert.com/svg-to-png
- https://svgtopng.com/
- Just upload the SVG files and download PNGs

#### Option 3: Design Tools
- **Inkscape** (free): File → Export PNG Image
- **Adobe Illustrator**: File → Export → Export As → PNG
- **GIMP**: Open SVG → File → Export As → PNG

---

## 📸 STILL NEEDED — Screenshots

### Required: 2-8 screenshots (recommended 8)

#### Screenshot 1: Dashboard/Home
Show recent transactions list with automatic SMS detection

#### Screenshot 2: Analytics/Charts
Show spending by category chart (pie/bar chart)

#### Screenshot 3: Categories
Show category breakdown and management

#### Screenshot 4: Security Settings
Show biometric app lock toggle

#### Screenshot 5: Senders/Banks
Show list of supported banks/senders

#### Screenshot 6: Backup/Restore
Show encrypted backup export feature

#### Screenshot 7: Goals/Budgeting
Show savings goals tracking

#### Screenshot 8: Search/Filter
Show transaction search and filter options

### Screenshot Specs
- **Format**: PNG or JPG
- **Size**: 1080px x 1920px minimum (9:16 aspect ratio)
- **Language**: English
- **No device frame**: Pure app content only

### How to Capture Screenshots

#### Android Emulator
```powershell
# Start emulator
emulator -avd <your-avd-name>

# Navigate through app, then capture:
adb shell screencap -p /sdcard/screenshot1.png
adb pull /sdcard/screenshot1.png D:\Development\SpendLens\store-listing\
```

#### Physical Device
1. Enable USB debugging
2. Navigate to app screen
3. Run:
```powershell
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png D:\Development\SpendLens\store-listing\
```

#### Android Studio
1. Run app on emulator/device
2. Android Studio → View → Tool Windows → Logcat
3. Click camera icon (Screen Capture)
4. Save to `store-listing/` folder

---

## 📋 PLAY STORE SUBMISSION CHECKLIST

### Main Listing
- [x] App name: SpendLens
- [x] Short description (80 chars)
- [x] Full description (~4000 chars)
- [x] Category: Finance → Budget & Expense Tracking
- [x] Tags/Keywords
- [ ] App icon PNG (512x512px) - ⚠️ Convert from SVG
- [ ] Feature graphic PNG (1024x500px) - ⚠️ Convert from SVG
- [ ] Screenshots (2-8 recommended) - ⚠️ Capture these
- [ ] Promo video (optional)

### Store Listing Details
- [x] Email: chauhangaurav101@gmail.com
- [ ] Phone number (optional)
- [x] Website: https://gachn.github.io/SpendLens/
- [x] Privacy Policy URL: https://gachn.github.io/SpendLens/privacy-policy.html
- [x] Data Deletion URL: https://gachn.github.io/SpendLens/data-deletion.html

### Content Rating
- [ ] Content rating questionnaire
- [ ] Target audience: Adults 18+ (bank accounts required)
- [ ] Geographic availability: India (primary)

### Release
- [ ] Release name: v1.0.0
- [ ] Release notes: "Initial release — automatic SMS expense tracking"
- [ ] Upload signed APK or AAB

### Data Safety
- [x] Data types disclosed (SMS, financial info, device ID, app activity, app performance)
- [x] Encryption in transit: Yes (Firebase HTTPS)
- [x] Encryption at rest: No (SQLCipher local, Firebase encryption on Google infrastructure)
- [x] Data required/optional:
  - SMS: Optional (runtime permission)
  - Firebase data: Required (no opt-out)
- [x] Purpose: App functionality, Analytics
- [x] Deletion request URL provided

---

## 🚀 SUBMISSION ORDER

1. **First**: Convert SVG graphics to PNG
2. **Second**: Capture screenshots (minimum 2)
3. **Third**: Complete Data Safety form
4. **Fourth**: Fill in Store Listing content
5. **Fifth**: Upload graphics and screenshots
6. **Sixth**: Upload signed APK/AAB
7. **Seventh**: Submit for review

---

## 📦 FILES SUMMARY

### Ready to Use
```
store-listing/
├── short-description.txt      ✅ Ready (80 chars)
├── full-description.txt       ✅ Ready (with your email)
├── metadata.md                ✅ Reference guide
├── app-icon.svg               ⚠️ Convert to PNG
├── feature-graphic.svg        ⚠️ Convert to PNG
└── convert-svg-to-png.bat     ⚠️ Run this script
```

### To Create
```
store-listing/
├── app-icon.png              ⚠️ Create (512x512px)
├── feature-graphic.png       ⚠️ Create (1024x500px)
├── screenshot1.png           ⚠️ Capture
├── screenshot2.png           ⚠️ Capture
├── ...
└── screenshot8.png           ⚠️ Capture
```

---

## 🎯 NEXT STEPS

1. **Convert graphics** (5 min)
   - Use online converter or run the batch file

2. **Capture screenshots** (15-30 min)
   - Run app on emulator or device
   - Navigate to each screen
   - Use ADB or Android Studio to capture

3. **Complete Play Store form** (10-15 min)
   - Upload converted graphics
   - Upload screenshots
   - Paste descriptions
   - Fill contact details

4. **Submit for review**
   - Upload signed APK/AAB
   - Submit and wait for review (1-3 days)

---

Need help with screenshots or conversion?