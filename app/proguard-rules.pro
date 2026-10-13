# Reglas de R8 para el release de AddonHub.
# OkHttp y Coil traen sus propias reglas; aquí solo se silencian avisos de librerías opcionales.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Los nombres de clases de estado (Stage, etc.) no se usan por reflexión, pero se conservan los
# números de línea para poder leer errores reales en los informes de fallos.
-keepattributes SourceFile,LineNumberTable
