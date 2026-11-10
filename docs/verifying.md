# Verifying a download

Every release on Forgejo and on the GitHub mirror carries a `SHA256SUMS` file. Check that the
file you downloaded matches it:

```sh
sha256sum -c SHA256SUMS --ignore-missing
```

## The APK signing certificate

Android checks an update against the certificate of the installed app, so this is what really
tells you an APK is ours. MeshConnect release APKs are signed with this certificate:

```
SHA-256: 91:2E:3E:B8:13:9A:33:52:C8:F1:B5:0A:2C:86:0D:3E:AB:63:52:EC:1A:F2:D9:42:86:9B:49:FB:F3:95:78:63
```

Check an APK with `apksigner verify --print-certs MeshConnect-X.Y.Z-release.apk`, or with
AppVerifier on the phone. Obtainium can pin this fingerprint, so an APK signed by anyone else
is refused.

Debug builds (`./build.sh debug`) are signed with a throwaway development key and install as a
separate app, `io.alvaroedo.meshconnect.debug`.
