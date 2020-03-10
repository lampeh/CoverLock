CoverLock
=========

Monitors proximity sensor and locks screen.

[![Android Builder](https://github.com/lampeh/CoverLock/workflows/Android%20Builder/badge.svg)](https://github.com/lampeh/CoverLock/actions?query=workflow%3A%22Android+Builder%22)<br>
[![Works on my Umidigi F1](.womm.png)](https://en.wikipedia.org/wiki/Umidigi "Works on my Umidigi F1")<br>


TODO:
-----
- configurable delay and other preferences
- don't lock if whitelisted apps are in foreground or camera in use (optional, maybe unreliable)
- pause button in notification / quick tile
- new icon
- license: MIT, GPL, Whatever?


Limitations:
------------
- Face Unlock, Fingerprint and other "weak" authentication cannot be used after locking the device with DevicePolicyManager.lockNow()
  - "After this method is called, the device must be unlocked using strong authentication (PIN, pattern, or password)" [DevicePolicyManager#lockNow()](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#lockNow())
  - DISABLE_KEYGUARD_FEATURES won't work, either: [Device admin deprecation](https://developers.google.com/android/work/device-admin-deprecation)
  - Trust Agent API not available
  - any other "lock **now**" method available?
  - see also: EMM DPC OCD
