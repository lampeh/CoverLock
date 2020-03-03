CoverLock
=========

Monitors proximity sensor and locks screen.

TODO:
-----
- Configurable delay and other preferences
- Un-register sensor listener on lock, re-register on screen on event (optional, might save battery)
- FULL_WAKE_LOCK is deprecated but ACQUIRE_CAUSES_WAKEUP does not work with PARTIAL_WAKE_LOCK. Ignore, Retry?
- don't lock if whitelisted apps are in foreground or landscape mode (optional, would I really use that?)
- wake only if locked by this app
- new icon
- license: MIT, GPL, Whatever?


Limitations:
------------
- Face Unlock, Fingerprint and other "weak" authentication cannot be used after locking the device with DevicePolicyManager.lockNow()
  - "After this method is called, the device must be unlocked using strong authentication (PIN, pattern, or password)" [DevicePolicyManager#lockNow()](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#lockNow())
  - DISABLE_KEYGUARD_FEATURES won't work, either: [Device admin deprecation](https://developers.google.com/android/work/device-admin-deprecation)
  - any other "lock *now*" method available?
  - Trust Agent API not available
  - see also: EMM DPC OCD


![Works on my Umidigi F1](.womm.png)
