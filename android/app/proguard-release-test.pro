# Applied to the APP only when building with -Pkeepr.testBuildType=release:
# the CI release check (see docs/RELEASE.md and scripts/release-upgrade-check.sh).
# Never part of a build that is distributed.
#
# The on-device ReleaseUpgradeTest calls the app's own classes directly, and
# the test APK leaves out every library the app already contains (Kotlin,
# coroutines, and AndroidX pieces the test runner uses), calling the app's
# copies instead. R8 removes or renames whatever the app itself doesn't use, so
# without these rules the test could fail with NoSuchMethodError for reasons
# that say nothing about the release build. Everything else (Compose, Room,
# the rest of AndroidX, resources) is still minified and shrunk as usual. The
# production configuration, without this file, is installed, launched and
# exercised separately by the same check.
-keep class com.chrisallenclark.keepr.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
# Used by the AndroidX test runner and test core libraries.
-keep class androidx.tracing.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.concurrent.** { *; }
-keep class com.google.common.util.concurrent.** { *; }
