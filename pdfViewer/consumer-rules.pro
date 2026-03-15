# Keep Kotlin-generated anonymous classes for lifecycle-process (ProcessLifecycleOwner).
# lifecycle-process 2.7+ was rewritten in Kotlin and generates anonymous classes with
# Kotlin 2.0 naming (e.g. ProcessLifecycleOwner$initializationListener$1).
# Without these rules, R8 minification in consumer apps can strip these classes,
# causing NoClassDefFoundError at startup when AppStartup initializes the lifecycle.
-keep class androidx.lifecycle.ProcessLifecycleOwner { *; }
-keep class androidx.lifecycle.ProcessLifecycleOwner$* { *; }
