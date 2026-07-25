# Firebase Crash Reporting and Analytics

SpendLens uses Firebase Crashlytics for crash reporting and Firebase Analytics for event logging to monitor app stability and user behavior.

## Setup

### Dependencies

Firebase integration is included via the following dependencies in `gradle/libs.versions.toml`:

- `firebase-crashlytics-ktx` - Crash reporting
- `firebase-analytics-ktx` - Event analytics
- `firebase-config-ktx` - Remote configuration

### Initialization

Firebase is automatically initialized in `MainActivity.onCreate()`:

```kotlin
FirebaseApp.initializeApp(this)
FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(true)
FirebaseHelper.initialize(this)
```

## Usage

### FirebaseHelper Utility

The `FirebaseHelper` object provides a centralized interface for Firebase operations:

```kotlin
import com.spendlens.app.util.FirebaseHelper

// Crash Reporting
FirebaseHelper.recordException(throwable)
FirebaseHelper.log("Custom log message")
FirebaseHelper.setCustomKey("user_type", "premium")

// Analytics
FirebaseHelper.logEvent("button_click", mapOf(
    "button_name" to "save_transaction",
    "screen" to "transaction_detail"
))

// User Identification
FirebaseHelper.setUserId("user_123")
```

### Crashlytics Features

- **Automatic crash reporting** - All uncaught exceptions are automatically reported
- **Custom logging** - Add context to crash reports with `FirebaseHelper.log()`
- **Custom keys** - Add custom key-value pairs to crash reports
- **User identification** - Associate crashes with specific users
- **Non-fatal exceptions** - Report caught exceptions that are important to track

### Analytics Features

- **Event logging** - Track user interactions and app events
- **User properties** - Set user-specific attributes
- **Screen tracking** - Automatically tracks screen views
- **Campaign attribution** - Tracks marketing campaign performance

## Recommended Events

### User Engagement
- `app_open` - App launched
- `screen_view` - Screen navigation
- `button_click` - Button interactions
- `transaction_added` - New transaction created
- `transaction_edited` - Transaction modified

### Features
- `sms_permission_granted` - SMS permission authorized
- `backup_created` - Data backup completed
- `pattern_learned` - New pattern learned
- `ai_categorization` - AI categorization performed

### Errors
- `parsing_error` - SMS parsing failure
- `sync_error` - Data synchronization error
- `permission_denied` - Permission request denied

## Privacy Considerations

- Firebase Crashlytics and Analytics are enabled by default
- Both can be disabled per user preference using:
  ```kotlin
  FirebaseHelper.setCrashlyticsCollectionEnabled(false)
  FirebaseHelper.setAnalyticsCollectionEnabled(false)
  ```
- No personally identifiable information (PII) is automatically collected
- Custom keys and events should not contain sensitive user data

## CI/CD Integration

Firebase configuration is handled via:
- `google-services.json` - Firebase project configuration (committed to repo)
- `google-services` Gradle plugin - Processes Firebase config during build

The GitHub Actions workflow (`.github/workflows/deploy-play.yml`) automatically uploads crash symbols to Firebase for release builds.

## Monitoring

Access Firebase Console at:
- **Crashlytics**: https://console.firebase.google.com/project/[PROJECT_ID]/crashlytics
- **Analytics**: https://console.firebase.google.com/project/[PROJECT_ID]/analytics

## Troubleshooting

### Crashes not appearing
1. Check Firebase project configuration
2. Verify `google-services.json` is correct
3. Ensure network connectivity on test device
4. Check that Crashlytics is enabled in Firebase console

### Analytics events not logging
1. Verify Analytics collection is enabled
2. Check event names match Firebase console schema
3. Ensure proper parameter types (String, Long, Double, Boolean)
4. Allow time for data processing (usually real-time, but can take up to 24 hours)

## Migration from NewRelic

This implementation replaces the previous NewRelic telemetry SDK. Key differences:
- **Firebase**: Free tier with generous limits, better mobile app support
- **NewRelic**: Previously used but removed to comply with NFR-1.1 (no analytics requirement)
- Firebase provides better integration with Google Play Store and other Google services