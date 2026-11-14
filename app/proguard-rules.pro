# Houston release shrinking rules.
# Debug stays unminified; these only apply to minified release builds.

# AIDL task-switcher interface used by Shizuku user service.
-keep class com.astroboii47.commander.ITaskSwitcherService { *; }
-keep class com.astroboii47.commander.TaskSwitcherService* { *; }

# Shizuku provider must keep its authority wiring.
-keep class rikka.shizuku.** { *; }
-keep class androidx.startup.** { *; }

# JSON backup / ranking use org.json reflectively via keys() iteration.
-keepclassmembers class org.json.JSONObject {
    public *;
}

# Markwon + Compose: keep Composable entry points referenced from XML themes.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*

# LiteRT / ML Kit GenAI: keep native bridge entry points, allow R8 to shrink the rest.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
-dontwarn com.google.mlkit.genai.**
