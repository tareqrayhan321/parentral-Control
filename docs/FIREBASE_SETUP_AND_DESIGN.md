# Firebase backend — design and setup

## 0. Google sign-in checklist (parent)
- Firebase Android app package name must be exactly `com.aistudio.parentalcontrol.pxmz` (the app's applicationId).
- Add SHA-1 + SHA-256 of the debug AND release keys, then re-download `google-services.json`.
- Authentication -> Sign-in method -> enable Google (this creates the Web client id that becomes `default_web_client_id`).
- Without google-services.json the app still builds and runs; sync is simply disabled and the UI says Firebase is not configured.

## 1. Identity
| Who | Firebase Auth | uid means |
|---|---|---|
| Parent | Google or email sign-in (NOT anonymous) | family id |
| Child phone | Anonymous sign-in | device id |

Rules reject an anonymous user acting as parent, so a child cannot promote itself.

## 2. Pairing (replaces the fake 6-digit code and the hardcoded HMAC secret)
1. Parent app creates `pairings/{token}`; token = 128-bit random (UUID without dashes), expiry <= 30 min.
2. Token is shown as a QR. A typed 6-digit code is NOT used: 10^6 combinations cannot be protected without server-side rate limiting.
3. Child scans the QR, signs in anonymously, then in ONE batch: updates the pairing to `claimed` (claimedBy = own uid) and creates `families/{parent}/devices/{child}`. Rules validate the token's post-write state with `getAfter`, including expiry, parent, and claimant. A token works once.
4. `list` on `pairings` is denied, so tokens cannot be enumerated.

## 3. Data model
```
pairings/{token}
families/{parentUid}
  devices/{childUid}            deviceName, platform, appVersion, pairedAt, pairingToken
    policy/current              version, updatedAt, apps{}, schedules{}      (parent writes)
    status/heartbeat            lastSeen, ackedPolicyVersion, isDeviceOwner, accessibilityEnabled, appVersion  (child writes)
    inventory/current           apps[{pkg,name}], updatedAt                  (child writes)
    usage/{yyyy-MM-dd}          minutesByPackage{}, updatedAt                (child writes)
    audit/{id}                  event, details, createdAt                    (child appends, immutable)
```

## 4. Guarantees the rules enforce
- Only the parent writes policy; version must be exactly previous + 1 (matches the app's monotonic check).
- Only the child writes status/usage/audit; `lastSeen` must equal server time, so online status cannot be forged by the client.
- Parent unlinking deletes the device doc; child access to everything under it ends immediately, and the child detects it (device doc missing / permission denied) and de-enrolls.
- A child cannot delete its own link. Child-initiated unpair must stay behind the parent PIN in the app and, for real protection, be requested by the parent.
- Everything else is denied.

## 5. What rules cannot do (be aware)
- No rate limiting. Mitigation: App Check (Play Integrity) enforced on Firestore and Auth.
- No proof the child phone is genuine. Heartbeat fields like isDeviceOwner are self-reported.
- A rooted child device can stop the app. Real tamper resistance needs Device Owner.
- Policy is trusted by the child because only the parent uid can write it. Per-policy signing is a possible later hardening.

## 6. Setup you must do in the Firebase console (cannot be done from code)
1. Create a Firebase project; add Android app with the app's real package name.
2. Add SHA-1 and SHA-256 of your debug and release keys (needed for Google sign-in and App Check).
3. Authentication -> enable **Anonymous** and your parent provider (Google and/or Email).
4. Firestore -> create database (production mode).
5. Download `google-services.json` into `app/` (do NOT commit it to a public repo; the zip had none).
6. App Check -> register Play Integrity; enforce on Firestore and Authentication after testing.
7. Deploy rules: `cd firebase && npx firebase deploy --only firestore:rules`.

## 7. Rules tests
`cd firebase && npm install && npm test` (needs Java for the Firestore emulator). The checked-in suite covers pairing, policy ownership/versioning, child telemetry, audit immutability, and unlink behavior.

## 8. Runtime layout (child phone)
- `ChildSyncProvider` holds ONE `ChildSyncController` per process. It owns its coroutine scope.
- `ParentalMonitoringService` (foreground) starts it, so policy pull / heartbeat / inventory keep running with the app closed.
- `BootReceiver` restarts the service after reboot when the device is paired.
- The UI only calls `start()` (idempotent) and listens to `linkLost`.
- QR scanning uses the Google code scanner (play-services-code-scanner): needs Google Play services, no CAMERA permission.
