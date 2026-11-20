# 保持 Compose 运行时与更新模块反射相关类
-keep class com.suibian.sunfile.update.** { *; }
-dontwarn org.json.**
# MT 插件解释器用反射调这些类的方法，名字不能被改
-keep class com.suibian.sunfile.data.mtapi.** { *; }
-keep class com.suibian.sunfile.data.mtjava.** { *; }
-dontwarn com.android.apksig.**

# Smali 反汇编 / 汇编：dexlib2 与 antlr 生成的 parser 都是直接调用，
# 但 R8 不认识它们的入口，整体保留最稳妥
-keep class org.jf.** { *; }
-keep class org.antlr.** { *; }
-dontwarn org.jf.**
-dontwarn org.antlr.**
-dontwarn com.google.common.**
-dontwarn javax.annotation.**
