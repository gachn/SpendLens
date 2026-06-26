# SQLCipher native bindings
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# Room generated code
-keep class androidx.room.** { *; }

# Keep entity classes (reflection by Room)
-keep class com.spendlens.app.data.db.** { *; }

# Kotlin metadata
-keepattributes *Annotation*, Signature, InnerClasses

# New Relic — line numbers for crash/log deobfuscation (agent plugin also injects rules)
-keepattributes SourceFile,LineNumberTable
-keep class com.newrelic.** { *; }
-dontwarn com.newrelic.**
