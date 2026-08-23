# Debug keystore

`debug.keystore` in this directory is checked in on purpose. It exists so
every build - a local Android Studio run and every CI build alike - signs
the debug APK with the *same* key.

Without this, each build would be signed with whatever debug keystore
happens to already exist on the machine building it. On a real dev machine
that's `~/.android/debug.keystore`, generated once and reused forever. But
GitHub Actions runners are a fresh, throwaway VM on every single run, so
without a committed keystore, Android Studio/AGP would auto-generate a
*brand-new, differently-signed* debug key on every CI build - and Android
refuses to install an update over an app signed with a different
certificate, forcing an uninstall before every reinstall.

This is **not** a secret. It uses the same publicly-documented, universal
defaults Android tooling itself uses for every developer's local debug
keystore (alias `androiddebugkey`, store/key password `android`) - it's
never used to sign a release build or publish to Google Play, only to let
this app self-update in place on a device.

To regenerate it (e.g. if you want a keystore unique to your own fork):

```
keytool -genkeypair -v \
  -keystore keystore/debug.keystore \
  -storetype PKCS12 \
  -storepass android -keypass android \
  -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

If you do, every device with the app already installed under the old key
will need one uninstall to pick up the new one - after that, updates work
in place again as usual.
