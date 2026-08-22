# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Room entities/DAOs are annotation-processed at compile time (KSP), no extra
# rules typically needed, but keep entities safe just in case reflection is
# ever used by a future migration helper.
-keep class com.mcserverstatus.app.data.** { *; }
