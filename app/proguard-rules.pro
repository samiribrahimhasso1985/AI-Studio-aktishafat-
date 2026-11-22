# PdfBox-Android uses reflection for fonts/resources
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
# Room entities
-keep class com.iktishafat.teleprompter.data.db.** { *; }
