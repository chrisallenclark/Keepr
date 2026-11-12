# Releasing Keepr for Android to testers

This page sets up, once, everything needed to send a properly signed Keepr build to
testers' phones, and then explains how each release goes out. You don't need Android
Studio, a Mac or anything installed on your computer: every step happens in a web browser.

There are two kinds of Android build:

| | Debug build | Release build |
|---|---|---|
| Where it comes from | Every push, `Android` workflow → artifact `keepr-debug-apk` | Only when you start the `Android release` workflow |
| Signed with | A throwaway key GitHub makes for each run | **Keepr's permanent release key**, which you create below |
| Who it's for | Developers and CI | Testers |
| Extras | Settings → Development (Sample data, Design Gallery); screenshot launch options | None of those |
| Delivered by | Download from GitHub | Firebase App Distribution (email invite) |

## Before you start: this repository is public

`github.com/chrisallenclark/Keepr` is public. That means:

* **Anyone can read the workflow logs and download workflow artifacts.** The workflows
  are written so that nothing secret is ever printed, and the signed release APK is
  never uploaded as an artifact. The debug APK artifact is public, which is fine:
  it's a test build signed with a throwaway key.
* **Secrets you add in GitHub's settings are not public.** GitHub hides them from
  logs, never gives them to pull requests from forks, and (with the environment set up
  below) only hands them to the release workflow after you approve that run.
* **Never commit the keystore file, its passwords or the Firebase key file** to the
  repository, not even briefly: git history is public and permanent.
* The release key's **SHA-256 fingerprint** is printed in the release workflow's
  summary. That's fine: a fingerprint is public by design (it identifies the key and
  can't be used to sign anything). It lets you check that the right key was used.

---

## 1. Create the permanent release key (once)

Android only installs an update if it's signed with the same key as the version
already on the phone. This key is therefore permanent: every release, forever, is
signed with it.

You'll make it in **Google Cloud Shell**, a free Linux terminal in your browser that
already has the `keytool` program. Nothing is installed on your computer.

1. Go to **https://shell.cloud.google.com** and sign in with your Google account.
   Wait for the terminal at the bottom to say it's ready.
2. **Choose a strong password** and save it in your password manager *before* going
   on. To have one generated, paste this and press Enter:

   ```sh
   openssl rand -base64 24
   ```

   Copy the line it prints (32 characters) into a new password-manager entry called
   "Keepr Android release key". This is both the *keystore password* and the *key
   password* (they are the same for this kind of keystore).
3. **Create the key.** Paste this, press Enter, and type the password from step 2
   when asked (twice; nothing shows as you type):

   ```sh
   keytool -genkeypair -v \
     -keystore keepr-release.jks -storetype PKCS12 \
     -alias keepr -keyalg RSA -keysize 4096 -validity 10000 \
     -dname "CN=Keepr"
   ```

   `-alias keepr` is the key's name (the *key alias*). `-validity 10000` is about 27
   years. `CN=Keepr` is the name inside the certificate; you can put your own name or
   company instead, but remember that anyone with the APK can read it.
4. **Note the fingerprint**, so you can check releases later:

   ```sh
   keytool -list -v -keystore keepr-release.jks -alias keepr | grep SHA256
   ```

   Save the `SHA256: AB:CD:…` line in the same password-manager entry.
5. **Turn the keystore into text** for GitHub:

   ```sh
   base64 -w 0 keepr-release.jks > keepr-release.jks.base64.txt
   ```

## 2. Back the key up safely (once)

**If the key is lost, nobody can ever update the installed app again.** Every tester
would have to uninstall Keepr (losing everything they recorded in it) and install a new
build signed with a new key. If someone else gets the key *and* its password, they
could sign an app that phones accept as an update to Keepr. So keep it safe and keep
it in more than one place.

1. In Cloud Shell, open the **⋮ (More)** menu at the top of the terminal → **Download**,
   and download `keepr-release.jks`. Then do the same for
   `keepr-release.jks.base64.txt` (you need it in step 3).
2. Store `keepr-release.jks` in **two** places, for example:
   * attached to the "Keepr Android release key" entry in your password manager, and
   * an encrypted USB stick or a second password manager / encrypted cloud folder.

   Keep the password with it (the password manager entry), but never in a plain text
   file next to the keystore.
3. After finishing step 3 below, **delete the files from Cloud Shell** (its home folder
   is kept between sessions):

   ```sh
   rm -f keepr-release.jks keepr-release.jks.base64.txt
   ls
   ```

   And delete the downloaded `keepr-release.jks.base64.txt` from your computer's
   Downloads folder.

## 3. Add the secrets and the protected environment in GitHub (once)

An *environment* is a locked box of secrets that only the release workflow can open,
and only after you approve each run.

1. Go to **github.com/chrisallenclark/Keepr → Settings → Environments → New
   environment**. Name it exactly **`android-release`** → **Configure environment**.
2. Under **Deployment protection rules**, tick **Required reviewers** and add yourself.
   Leave **Prevent self-review** **unticked** (it is off by default). That option stops
   the person who started a run from approving it, and you are both the person who
   starts releases and the only reviewer, so with it on no release could ever be
   approved. Click **Save protection rules**.
3. Under **Deployment branches and tags**, choose **Selected branches and tags** and
   add `main` and `claude/friendly-pasteur-nft1vl` (the Android branch). The workflow
   also refuses any other branch on its own, and Google (step 5) only trusts runs from
   these branches.
4. Under **Environment secrets → Add environment secret**, add each of these. The name
   must match exactly:

   | Name | Value |
   |---|---|
   | `KEEPR_KEYSTORE_BASE64` | The whole contents of `keepr-release.jks.base64.txt` (one long line). Open the file in a text editor, select all, copy, paste. |
   | `KEEPR_KEYSTORE_PASSWORD` | The password from step 1.2 |
   | `KEEPR_KEY_ALIAS` | `keepr` |
   | `KEEPR_KEY_PASSWORD` | The same password again |

   The Firebase settings are not secrets and are added as **environment variables** in
   step 5. No Google key is stored anywhere (see step 5.5).
5. Recommended: **Settings → Actions → General → Fork pull request workflows from
   outside collaborators** → "Require approval for all outside collaborators". (Fork pull
   requests never receive these secrets anyway; this just avoids surprises.)

Add secrets to the **environment**, not to *Settings → Secrets and variables →
Actions → Repository secrets*: repository secrets are available to every workflow
without approval.

## 4. Versions

Every Android build has two version numbers:

* **Version name**, what people see (Settings → About shows "0.4.0 (1003)"). It is set
  in exactly one place: the line `keepr.versionName=0.4.0` in `android/gradle.properties`.
  Change it there when you start a new version.
* **Version code**, a whole number Android uses to decide whether an APK is an update.
  A phone only accepts an update with a **higher** code than what's installed. You
  never set it by hand: the release workflow uses **1000 + the workflow's run number**,
  so the first release is 1001, the next 1002 and so on, every release higher than the
  last (trial runs use up numbers too; that's fine, gaps don't matter).
  * If the run number ever starts again from 1 (it does if the workflow file is renamed
    or deleted and recreated), add a variable so codes keep going up: **Settings →
    Environments → android-release → Environment variables → Add variable**, name
    `KEEPR_VERSION_CODE_OFFSET`, value a number big enough that offset + run number is
    above the last release (e.g. `5000`).
* Local and debug builds always have version code **1**, below every release.

**Debug and release builds can't update each other**, because they're signed with
different keys, whatever their version codes. On one phone, use one or the other. To
switch, uninstall Keepr first, which deletes everything recorded in it. Debug builds
from different CI runs are each signed with a different throwaway key, so they can't
update each other either.

## 5. Firebase App Distribution (once)

Firebase App Distribution emails testers an invite and lets them install and update the
app. Keepr itself contains no Firebase code and talks to no server: Firebase is only
the delivery van.

1. Go to **https://console.firebase.google.com** → **Create a project** (e.g. "Keepr").
   Google Analytics is not needed; turn it off.
2. On the project overview, click **Add app → Android**. Package name:
   **`com.aulorvy.keepr`** (exactly). Nickname: "Keepr Android". Leave the SHA-1
   empty. **Register app**. Skip "Download google-services.json" and "Add Firebase SDK"
   (Keepr doesn't use them): click **Next** / **Continue to console**.
3. **App ID:** ⚙ **Project settings → General → Your apps → Keepr Android → App ID**, a
   value like `1:123456789012:android:0a1b2c3d4e5f6a7b`. Add it as the environment
   **variable** **`FIREBASE_APP_ID`**.
4. **Testers:** left menu **Run → App Distribution** (or *Release & Monitor → App
   Distribution*) → **Get started**. Open **Testers & Groups → Add group**, name it e.g.
   "Testers". Note the group's **alias** shown under its
   name (e.g. `testers`) and add it as the environment **variable** **`FIREBASE_TESTER_GROUP`**.
   Don't add anyone yet: testers are invited only when you decide to.
5. **Let GitHub upload without a stored key** (*Workload Identity Federation*). Instead
   of a permanent key file, Google is told to trust the short-lived identity token
   GitHub gives this one workflow, in this repository, in the `android-release`
   environment, on `main` or the Android branch, and nothing else. A leaked log or a
   compromised fork has nothing to steal.
   1. Open **https://shell.cloud.google.com** (signed in with the account that owns the
      Firebase project).
   2. Paste these lines, replacing `your-firebase-project-id` with the **Project ID**
      from ⚙ Project settings → General (not the display name). Press Enter after each
      block; each takes a few seconds.

      ```sh
      PROJECT_ID=your-firebase-project-id
      gcloud config set project "$PROJECT_ID"
      PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
      SA="keepr-distribution@$PROJECT_ID.iam.gserviceaccount.com"
      gcloud services enable iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com firebaseappdistribution.googleapis.com
      ```

      ```sh
      gcloud iam service-accounts create keepr-distribution \
        --display-name="Keepr: GitHub uploads to App Distribution"
      gcloud projects add-iam-policy-binding "$PROJECT_ID" \
        --member="serviceAccount:$SA" --role="roles/firebaseappdistro.admin"
      ```

      ```sh
      gcloud iam workload-identity-pools create github \
        --location=global --display-name="GitHub Actions"
      gcloud iam workload-identity-pools providers create-oidc keepr-release \
        --location=global --workload-identity-pool=github \
        --display-name="Keepr release workflow" \
        --issuer-uri="https://token.actions.githubusercontent.com" \
        --attribute-mapping="google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id,attribute.event_name=assertion.event_name,attribute.environment=assertion.environment,attribute.ref=assertion.ref,attribute.workflow_ref=assertion.workflow_ref" \
        --attribute-condition="assertion.repository_id=='1333641349' && assertion.repository_owner_id=='184265033' && assertion.event_name=='workflow_dispatch' && assertion.environment=='android-release' && assertion.workflow_ref in ['chrisallenclark/Keepr/.github/workflows/android-release.yml@refs/heads/main', 'chrisallenclark/Keepr/.github/workflows/android-release.yml@refs/heads/claude/friendly-pasteur-nft1vl']"
      ```

      What that last line means: Google accepts a GitHub token only if it comes from
      **this repository and this account** (checked by their permanent numeric IDs,
      which unlike names can't be taken over if a repository is ever renamed or
      deleted), from a run **started by hand**, in the **`android-release`**
      environment (so after your approval), running **the release workflow file
      itself** from **`main` or the Android branch**. A pull request, a fork, another
      workflow, another branch or a tag all get "permission denied".

      ```sh
      gcloud iam service-accounts add-iam-policy-binding "$SA" \
        --role="roles/iam.workloadIdentityUser" \
        --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository_id/1333641349"
      echo "GCP_WORKLOAD_IDENTITY_PROVIDER = projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/keepr-release"
      echo "GCP_SERVICE_ACCOUNT = $SA"
      ```

      **Permissions.** The service account gets one role, **Firebase App Distribution
      Admin**, on the Keepr Firebase project only. It's the narrowest role Google
      offers that can upload a build and send it to a group: it covers App
      Distribution and nothing else (not your Google account, billing, other Firebase
      products or other projects). Use a Firebase project that holds only Keepr, so
      even that reaches nothing else.
   3. The last two lines print two values. Add each as an **environment variable** (not
      a secret; they aren't sensitive) in **Settings → Environments → android-release →
      Environment variables → Add variable**, with exactly those names.
   4. If you later rename the Android branch or release from another one, update the
      `--attribute-condition` (the same command with `update-oidc` instead of
      `create-oidc`, and without `--issuer-uri`) and the branch list in
      `android-release.yml`.

Everything in the `android-release` environment, for reference:

| Name | Kind | From |
|---|---|---|
| `KEEPR_KEYSTORE_BASE64` | secret | Step 1.5 |
| `KEEPR_KEYSTORE_PASSWORD` | secret | Step 1.2 |
| `KEEPR_KEY_ALIAS` | secret | `keepr` |
| `KEEPR_KEY_PASSWORD` | secret | Step 1.2 (same password) |
| `FIREBASE_APP_ID` | variable | Step 5.3 |
| `FIREBASE_TESTER_GROUP` | variable | Step 5.4 |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | variable | Step 5.5 |
| `GCP_SERVICE_ACCOUNT` | variable | Step 5.5 |

## 6. Making a release

**Why there's no "Run workflow" button yet.** GitHub only shows that button for
workflows that are on the default branch (`main`). The release workflow stays on the
Android branch until you approve merging the Android work, so it is started through
GitHub's API instead, which works for any branch. The run then waits for your approval
exactly as it would from the button, and it uses the workflow and app code from the
Android branch.

1. **Start the run**, either way:
   * **Ask Claude** in your Keepr Android session: "start a trial release build"
     (distribution off) or "start a release and send it to testers". It starts the
     run through the API as you; nothing runs without your approval in step 2.
   * **Or yourself**, in Google Cloud Shell (it has the GitHub CLI): run
     `gh auth login` once, then

     ```sh
     gh workflow run android-release.yml --repo chrisallenclark/Keepr \
       --ref claude/friendly-pasteur-nft1vl -f distribute=false
     ```

     (`-f distribute=true -f release_notes="What's new"` to send it to testers.)

   Once the Android work is merged into `main`, you can use **Actions → Android
   release → Run workflow** instead.
2. Open **Actions → Android release** and the new run. It waits with **"Review
   deployments"**. Click it, tick `android-release`, **Approve and deploy**. (This
   approval is what lets the run use the secrets and sign in to Firebase.)
3. The first time, use **distribute off**: a trial that builds, tests and checks the
   signed APK without sending it to anyone.
4. When it finishes, open the run's **Summary**. Check:
   * the version is what you expect, and
   * **Signing certificate SHA-256** matches the fingerprint you saved in step 1.4
     (same hex pairs; the summary prints them without colons, in lowercase).

   If a secret or variable is missing or wrong, the run fails with a red message
   naming it.
5. When you're happy, run it again with **distribute on** and some **release notes**.
   Approve it the same way. Testers in the group get an email.

Each run also saves a small `keepr-release-mapping-<code>` artifact. It isn't the app;
it turns a tester's crash report back into readable names. It contains nothing secret.

## 7. What testers do

1. They get an email from Firebase App Distribution: **"You've been invited to test
   Keepr"**. On their Android phone, they open it and tap **Get started**, then sign
   in with the Google account the invite was sent to and accept.
2. Firebase offers its **App Tester** app (it may suggest installing it as a home screen
   shortcut or app). Either App Tester or the browser page lists Keepr's releases;
   they tap **Download** on the newest one.
3. Android asks to allow installing apps from this source ("Install unknown apps"):
   they tap **Settings**, turn on **Allow from this source** for the browser or App
   Tester, go back, and tap **Install**. Google Play Protect may say the app is from an
   unknown developer or offer to scan it; they can tap **Install anyway** (or *More
   details → Install anyway*).
4. **Updates:** when a new release goes out, they get another email (and see it in App
   Tester), download it and install it over the old one. Everything they recorded stays.
5. If they previously installed a **debug** build (from GitHub), Android refuses the
   release ("App not installed" / "conflicts with an existing package"). They must
   uninstall the debug build first, which deletes its data.

Release builds leave out the Settings → **Development** section (Sample data and the
Design Gallery). The iPhone app always shows it; on Android it's only in debug builds,
by choice, so testers don't load sample data into their real records.

---

## How the release build is checked on every push (for developers)

The `Android` workflow has a **Release build** job (API 26 and API 35 emulators) that
checks the release configuration on every push and pull request, without ever touching
the real key. It generates a throwaway key inside the job, builds from one commit:

| APK | What |
|---|---|
| `plain-100` | The release build exactly as distributed (R8, resource shrinking), version code 100 |
| `app-100`, `app-101` | The release build type with `-Pkeepr.testBuildType=release`, codes 100 and 101 |
| `test` | The androidTest APK for the release build type, signed with the same key |

and `scripts/release-upgrade-check.sh` then checks, printing PASS/FAIL lines:

1. `plain-100`: `dumpsys package` shows versionCode 100, no `DEBUGGABLE` flag, no
   `DebugReminderReceiver`, no `WRITE_CONTACTS`; it launches (ignoring debug-only screenshot
   extras), goes through onboarding and every tab without a crash (`pidof`, and no
   `FATAL EXCEPTION` for the package in logcat).
2. `app-100`: the same package checks; `ReleaseUpgradeTest#writeBeforeUpdate` stores a
   person with an imported-contact link, a note, a 70 KB file and a follow-up with a
   reminder three days out; a normal launch shows Today and the reminder's alarm is set.
3. `adb install -r app-101` (an update, not a reinstall): versionCode is 101;
   `RescheduleReceiver` logs its re-arm after `MY_PACKAGE_REPLACED` and the alarm is set
   again without opening the app; `ReleaseUpgradeTest#readAfterUpdate` finds every record,
   the file's exact bytes and the pending reminder at its original time; a normal launch
   shows Today and People.

Nothing signed is uploaded; only screenshots (`release-check-screenshots-*`). The key is
deleted at the end of the job.

Why `app-100`/`app-101` differ from `plain-100`: the test calls the app's classes directly,
so `-Pkeepr.testBuildType=release` adds `app/proguard-release-test.pro`, which keeps the
app's own classes, the Kotlin/coroutines libraries and the few AndroidX libraries the test
runner shares with the app from being removed or renamed by R8 (Compose, Room and the rest
of AndroidX are still minified). The production configuration is covered by
the `plain-100` launch-and-walk check.
