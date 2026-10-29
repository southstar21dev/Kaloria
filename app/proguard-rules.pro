# ML Kit (barcode, text recognition, GenAI) finds its components by instantiating
# ComponentRegistrar classes reflectively; R8 full mode would strip their no-arg constructors,
# and every ML Kit client then fails at runtime (NoSuchMethodException, then an NPE in the client).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
