# R8 rules for the androidTest APK when the tests run against the release
# build type (-Pkeepr.testBuildType=release, CI only). The test APK is
# processed only so that its references follow the app's renamed classes; the
# tests and the test runner must stay whole and keep their names, because
# scripts/release-upgrade-check.sh names the test class on the command line.
-dontshrink
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.aulorvy.keepr.** { *; }
-keep class androidx.test.** { *; }
-keep class org.junit.** { *; }
-keep class junit.** { *; }
-keep class org.hamcrest.** { *; }
# Other test classes reference Compose test APIs and app code that R8 removed
# from the minified app. They never run against a release build, so their
# missing references are not errors.
-ignorewarnings
-dontwarn **
