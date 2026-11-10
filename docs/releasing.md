# Releasing

A release is a tag. Pushing `vX.Y.Z` runs `.forgejo/workflows/release.yml`, which runs the
tests, builds and signs the APK, the AAB and the Accrescent APK set, and publishes them with
`SHA256SUMS` on Forgejo and on the GitHub mirror. This page covers the one-time set-up, then
each release, then the stores.

## One-time set-up

### 1. The signing key

Android installs an update only if it is signed with the same key as the installed app, so
this key is permanent: lose it and every user has to uninstall (losing their message history)
and reinstall. Make it once, keep two offline copies (for example an encrypted USB stick and
your password manager), and never commit it.

Use a key for MeshConnect alone, not one shared with HFPredict or Skypost. A leaked or
compromised key then affects one app, and the app can change hands, or move to a store that
holds keys, without taking the others with it.

Use the JDK the build already downloaded (`./build.sh setup` fetches it):

```sh
.tools/jdk-17/bin/keytool -genkeypair -v \
  -keystore meshconnect-release.jks -storetype PKCS12 \
  -alias meshconnect -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=Álvaro Edo Martínez, O=MeshConnect"
```

It asks for one password (a PKCS12 keystore uses the same password for the store and the
key). Then print the certificate's fingerprint and keep it with the key:

```sh
.tools/jdk-17/bin/keytool -list -v -keystore meshconnect-release.jks -alias meshconnect | grep SHA256
```

Put that fingerprint in [verifying.md](verifying.md): users (and Obtainium, AppVerifier and
F-Droid) check downloads against it.

To sign locally, outside CI:

```sh
MESHCONNECT_KEYSTORE=~/keys/meshconnect-release.jks MESHCONNECT_KEY_ALIAS=meshconnect ./build.sh release
MESHCONNECT_KEYSTORE=~/keys/meshconnect-release.jks MESHCONNECT_KEY_ALIAS=meshconnect ./build.sh accrescent
```

### 2. Forgejo secrets and variables

In the repository: *Settings → Actions → Secrets*.

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the output of `base64 -w0 meshconnect-release.jks` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `meshconnect` |
| `KEY_PASSWORD` | the same password again (PKCS12); optional, it defaults to the store's |
| `GH_RELEASE_TOKEN` | a GitHub fine-grained token for `alvaroemtnez/MeshConnect` only, with *Contents: read and write*. Unset, the GitHub release is skipped. |

And under *Settings → Actions → Variables*:

| Variable | Value |
| --- | --- |
| `GH_REPO` | `alvaroemtnez/MeshConnect` |

The Forgejo release itself uses the job's automatic token. Clear the base64 text from your
clipboard and shell history after pasting it.

### 3. The GitHub mirror

The mirror (`alvaroemtnez/MeshConnect`) is a push mirror from Forgejo. The release job creates
the GitHub release through the API once the mirror has pushed the tag, so the mirror must be
set to push tags (Forgejo's push mirrors do). Obtainium, the README's button, reads the GitHub
releases.

## Each release

1. Make sure `CHANGELOG.md` has a `## X.Y.Z` section, written for users, and that
   `AboutSection.kt` lists the same version (`./build.sh test` checks they agree). The release
   notes are this section; the workflow fails without one.
2. Add `fastlane/metadata/android/{en-US,es-ES,ca}/changelogs/<versionCode>.txt` (500
   characters at most), where versionCode is `X*10000 + Y*100 + Z` (1.0.0 is `10000`).
3. Run the checks: `./build.sh test`.
4. Commit, tag and push:

   ```sh
   git commit -am "Release X.Y.Z"
   git tag -a vX.Y.Z -m "MeshConnect X.Y.Z"
   git push && git push --tags
   ```

5. The workflow publishes `MeshConnect-X.Y.Z-release.apk`, `MeshConnect-X.Y.Z-release.aab`,
   `MeshConnect-X.Y.Z.apks` and `SHA256SUMS` on Forgejo and GitHub.

The version comes from the tag: there is no version string to bump in the code. Don't create
the release by hand as well; the job fails if it already exists.

## App stores

Each store has its own policy on AI-written code, and this app was written with an AI
assistant: read the current policy before submitting, and disclose it where asked.

### Obtainium

Nothing to do: it reads the release APKs from the GitHub mirror. Keep the asset name pattern
(`MeshConnect-<version>-release.apk`) stable, and in the app's Obtainium settings pin the
certificate fingerprint from [verifying.md](verifying.md).

### F-Droid

- Store text, icon and per-version changelogs: `fastlane/metadata/android/<locale>/` (en-US,
  es-ES, ca). Screenshots go in `images/phoneScreenshots/`; take them redacted
  (`tools/redact-screenshot.py`, see CLAUDE.md) or from the fake radio.
- Build recipe: `packaging/fdroid/io.alvaroedo.meshconnect.yml`. F-Droid brings its own JDK and
  SDK and runs Gradle in `androidApp/`; the version comes from the tag.
- Submit: fork <https://gitlab.com/fdroid/fdroiddata>, copy the recipe to
  `metadata/io.alvaroedo.meshconnect.yml`, check it with `fdroid lint` and
  `fdroid build -v -l io.alvaroedo.meshconnect` (their Docker image), and open a merge request.
- Expect the reviewer to ask about the map tiles (OpenFreeMap, switchable off in the map's menu)
  and the prebuilt native libraries inside the SQLCipher and MapLibre AARs from Maven Central;
  both projects are free software.
- After the first release, reproducible builds let F-Droid ship our signature instead of
  theirs, so an F-Droid install and a Forgejo/Obtainium install can update each other: add
  `AllowedAPKSigningKeys:` with the certificate's SHA-256 (lowercase, no colons) to the recipe.
- IzzyOnDroid, the other F-Droid repository, does not take apps written with generative AI.

### Accrescent

- The release attaches `MeshConnect-X.Y.Z.apks`: the app bundle split by bundletool and signed
  with the release key (v2 and v3 signatures, one signer). `./build.sh accrescent` builds the
  same locally. Accrescent never takes a debug-signed app.
- Register in the developer console, then upload the `.apks`. Accrescent pins the signing key
  of the first upload, so upload one signed with the key from step 1.
- The reviewer reads the manifest's permissions: Bluetooth (the radio; scanning is declared
  `neverForLocation`), location (the phone's position, shared only when you choose to; also
  what Bluetooth scanning needs on Android 11 and older), camera (the QR scanner), internet
  (map tiles, and TCP to a networked radio, off by default), notifications, the foreground
  service that keeps the radio connected, and start at boot. Have a sentence ready for each.
