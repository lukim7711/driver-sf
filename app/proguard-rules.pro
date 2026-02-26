# ================================================================
# ProGuard rules for PoC Screen Reader
# ================================================================
# These rules are needed when isMinifyEnabled = true (release build).
# Currently minification is disabled, but these rules are prepared
# for when we enable it for production.
# ================================================================

# ────────────────────────────────────────────────
# Room Database
# ────────────────────────────────────────────────
# Room entities (data classes mapped to DB tables)
# Room uses reflection for some operations, so entity classes
# and their fields must not be obfuscated.
-keep class com.driversfpoc.screenreader.data.model.CaptureRecord { *; }
-keep class com.driversfpoc.screenreader.data.model.FlowBoard { *; }
-keep class com.driversfpoc.screenreader.data.model.FlowBoardItem { *; }
-keep class com.driversfpoc.screenreader.data.model.FlowBoardItemWithCapture { *; }

# Room DAOs — keep method signatures for Room's generated impl
-keep interface com.driversfpoc.screenreader.data.CaptureDao { *; }
-keep interface com.driversfpoc.screenreader.data.FlowBoardDao { *; }

# ────────────────────────────────────────────────
# Gson
# ────────────────────────────────────────────────
# Gson uses reflection to serialize/deserialize objects.
# Without these rules, field names get obfuscated and JSON
# keys won't match (e.g. "a" instead of "className").

# Keep NodeData — serialized to JSON via Gson in ScreenReaderService
-keep class com.driversfpoc.screenreader.data.model.NodeData { *; }

# Gson TypeToken (needed for generic type resolution)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep @SerializedName annotations if used in future
-keepattributes *Annotation*

# Prevent R8 from stripping Gson's internal classes
-dontwarn com.google.gson.internal.**

# ────────────────────────────────────────────────
# General Android
# ────────────────────────────────────────────────
# Keep line numbers for crash reports (even in release)
-keepattributes SourceFile,LineNumberTable

# Hide original source file names in stack traces
-renamesourcefileattribute SourceFile
