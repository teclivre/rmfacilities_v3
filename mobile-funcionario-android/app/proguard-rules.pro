# Mantem metadados usados por Gson para tipos genericos.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# ---------------------------------------------------------------------------
# DTOs JSON do app
# ---------------------------------------------------------------------------
# Mantem TODAS as classes/atributos do pacote do app. Sem isso, em build release
# o R8 renomeia os campos das data classes usadas pelo Gson e o parser
# silenciosamente devolve objetos vazios -- ou, em alguns caminhos com primitivos
# Kotlin, lanca ClassCastException ao tentar usar copy()/equals em data classes
# obfuscadas.
-keep class br.com.rmfacilities.funcionarioapp.** { *; }
-keepclassmembers class br.com.rmfacilities.funcionarioapp.** {
    <init>(...);
    *;
}

# Preserva nomes das enums (necessario para serializacao Gson).
-keepclassmembers enum br.com.rmfacilities.funcionarioapp.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------------------
# Gson -- regras oficiais
# ---------------------------------------------------------------------------
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.TypeAdapter
-keep,allowobfuscation @interface com.google.gson.annotations.SerializedName
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------------------------------------------------------------------------
# Kotlin metadata + coroutines
# ---------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---------------------------------------------------------------------------
# OkHttp / outros warnings (defensivo)
# ---------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
