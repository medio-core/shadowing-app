# Shadowing app の R8 設定。
#
# media3 / play-services-ads / UMP / Firebase は各ライブラリが
# consumer ルールを同梱しているため、ここでの個別指定は不要。

# Crashlytics のスタックトレースを読める状態に保つ。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin のリフレクション経由で参照されうるメタデータを保持する。
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
