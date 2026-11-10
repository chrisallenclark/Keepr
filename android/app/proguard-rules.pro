# R8 rules for the release build.
#
# Keepr uses no reflection of its own: enums are looked up with `entries`,
# JSON goes through org.json (part of Android), and resources are referenced
# directly (R.drawable.*), so R8's defaults plus the consumer rules that
# AndroidX ships inside each library (Room, Compose, Core, Activity) cover the
# app. Add a rule here only alongside the code or dependency that needs it.

# Room finds the generated <Database>_Impl class by name. Room's own consumer
# rules already keep it; this copy makes sure a Room upgrade that changes its
# rules can't produce a release build that crashes on first launch.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Readable stack traces from testers: keep line numbers (the mapping file the
# release workflow saves turns the renamed classes back into real names).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
