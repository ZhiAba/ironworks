# 请把素材放进这个目录

我需要的文件（放这个目录即可，文件名随意）：

1. **v160.1 的 jar** —— 优先「原版桌面版」`Mindustry.jar`（io.anuke.mindustry 对应的 build）。
   编译 classpath 必须和运行版本一致，v157.3 的 jar 不能用来编 v160.1 的模组。
2. **v160.1 的 Java 源码包**（`Anuken/Mindustry` 对应 tag 的完整源码树，zip 即可）。

如果只有 APK：也能用，我会从 APK 里抽 classes.dex 做反查，但比给 jar 麻烦。

注意：`/sdcard/Android/data/` 对我不可读（Permission denied），
所以文件必须复制到 `/sdcard` 下可读的普通目录，比如这里。
