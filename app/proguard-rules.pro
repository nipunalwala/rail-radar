# Retrofit, OkHttp, kotlinx.serialization, Hilt, Room and WorkManager ship their
# own R8 rules. Only what they do not cover goes here.

# Keep line numbers so a crash report can be read.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retrofit reads the generic return type of suspend functions by reflection.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class com.trainnearme.provider.railradar.EnvelopeDto
