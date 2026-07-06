# Play Store CI/CD

[`.github/workflows/deploy-play.yml`](../.github/workflows/deploy-play.yml) builds a signed
release AAB and publishes it to the **production** track on every push to `main`. There's no
manual approval step — a push to `main` goes live to all users. If you want a safety gate before
that, see [Adding a manual approval gate](#adding-a-manual-approval-gate) below.

## Prerequisites (do these once, before the first automated push)

Google Play's Developer API cannot create a new app listing or its first release — that first
upload has to happen manually through the Play Console.

1. **Create the app listing in Play Console** (App content, store listing, content rating,
   target audience, data safety form, etc.). This app reads SMS, so Play's
   [SMS/Call Log permissions policy](https://support.google.com/googleplay/android-developer/answer/10208820)
   applies — you'll need to justify `READ_SMS`/`RECEIVE_SMS` usage and may be asked to declare
   this as a default-SMS-handler-adjacent use case. Get through that review before wiring up CI.
2. **Manually upload the first release** using the release keystore already in this repo's
   local setup (`keystore/release.keystore.jks`, see [BUILD.md](BUILD.md)):
   ```sh
   ./gradlew bundleRelease
   ```
   Upload `app/build/outputs/bundle/release/app-release.aab` via Play Console → your app →
   Production → Create release. When prompted, opt in to **Play App Signing** (Google re-signs
   the app for distribution; your keystore becomes the "upload key" — you still need it for every
   future upload, CI included).
3. **Create a Google Cloud service account** for CI uploads:
   - In [Google Cloud Console](https://console.cloud.google.com/), pick/create a project, enable
     the **Google Play Android Developer API**.
   - IAM & Admin → Service Accounts → Create service account (e.g. `play-ci-uploader`). Create a
     JSON key for it and download it — this file is a credential, handle it like a password.
   - In Play Console → Setup → API access, link the Cloud project if not already linked, find the
     service account, and grant it access with at minimum: **Release to production, exclude
     devices, and use Play App Signing** permission for this app (under "App permissions").

## Required GitHub secrets

Add these under repo **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64 of `keystore/release.keystore.jks` (see command below) |
| `ANDROID_KEYSTORE_ALIAS` | `spendlens-release` (or whatever alias you used) |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore's store password |
| `ANDROID_KEY_PASSWORD` | The key's password (same as store password for this repo's PKCS12 keystore) |
| `PLAY_SERVICE_ACCOUNT_JSON` | Full contents of the service account JSON key file |
| `OPENROUTER_API_KEY` | Optional — only if you want CI-published builds to ship a default OpenRouter key |
| `NEW_RELIC_APP_TOKEN` | Optional — only if you want CI-published builds to upload crash symbols to New Relic |

To base64-encode the keystore for the secret, without ever printing it to your terminal:

```sh
base64 -i keystore/release.keystore.jks -o keystore.b64
```

Then open `keystore.b64` in an editor (or use `pbcopy < keystore.b64` on macOS) to copy its
contents into the GitHub secret value field, and delete `keystore.b64` afterward.

## What the workflow does

1. Checks out the repo, sets up JDK 17.
2. Decodes the keystore secret to a temp file and builds `app-release.aab` via
   `./gradlew bundleRelease`, using `ANDROID_VERSION_CODE=${{ github.run_number }}` so every
   upload gets a strictly increasing `versionCode` (Play rejects duplicates/decreases).
3. Uploads the AAB to the `production` track via
   [`r0adkll/upload-google-play`](https://github.com/r0adkll/upload-google-play), fully rolled
   out (`status: completed`).

## Adding a manual approval gate

Since this pipeline auto-publishes to production with no review step, consider wrapping the
deploy job in a [GitHub Environment](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment)
(e.g. `production`) with required reviewers — the build still runs on every push, but the actual
Play upload step waits for someone to click approve. To do this, add `environment: production`
to the `build-and-deploy` job in the workflow and configure required reviewers on that
environment under repo Settings → Environments.
