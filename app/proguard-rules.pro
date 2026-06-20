# SQLCipher native bindings
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# Room generated code
-keep class androidx.room.** { *; }

# Keep entity classes (reflection by Room)
-keep class com.spendlens.app.data.db.** { *; }

# Kotlin metadata
-keepattributes *Annotation*, Signature, InnerClasses

# PostgreSQL JDBC driver for Neon cloud backup
-keep class org.postgresql.** { *; }
-dontwarn org.postgresql.**
