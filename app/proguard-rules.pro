# 保持 Compose 运行时与更新模块反射相关类
-keep class com.suibian.sunfile.update.** { *; }
-dontwarn org.json.**
# MT 插件解释器用反射调这些类的方法，名字不能被改
-keep class com.suibian.sunfile.data.mtapi.** { *; }
-keep class com.suibian.sunfile.data.mtjava.** { *; }
-dontwarn com.android.apksig.**
