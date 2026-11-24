# 云音源 9.5.81

网易云音乐 9.5.81 的内嵌模块（Xposed 模块，包名 `com.cloudmusic.source`），基于 [dolby_beta](https://github.com/nining377/dolby_beta) β 版二次开发，仅适配 9.5.81。

模块通过**自有音源接口替换播放地址**实现会员歌曲播放，不修改网易云服务端数据，也不破解歌曲本身。真心支持歌手请开通官方会员。

## 适配版本

| 项目 | 值 |
| --- | --- |
| 网易云音乐 | 9.5.81（versionName 9.5.81 / versionCode 9581） |
| 模块版本 | 9.5.81 / 9581 |
| ABI | arm64-v8a |
| 注入方式 | Xposed 模块（LSPosed / Xpatch）或内嵌进网易云安装包 |

## 功能

### 会员与播放

-   **本地会员权益**：改写 `UserPrivilege` / `RedPlus` 会员字段（黑胶 VIP、SVIP、会员中心入口按满级返回）
-   **自有音源替换**：Hook `HttpDataSource` / `ComplexDataSource` 的播放地址获取，空地址或试听片段时替换为自有音源（主 gdstudio，备 s0o1），按请求码率返回 128K / 192K / 320K / 无损
-   **并发 + 预热**：两个音源并发请求，App 启动时预热 DNS/TLS，避免等待过久导致播放器切歌；成功结果缓存 5 分钟，失败冷却 60 秒
-   **无损音质**：请求 999 码率时返回 FLAC
-   **下载**：会员歌曲走替换后的地址下载
-   **一起听蒙面解锁**：直接查看对方信息，无需对方解除蒙面

### 界面与体验

-   **底部导航精简**：只保留「首页 / 我的」
-   **移除 Banner 与红点**
-   **播放页「免」标签隐藏**
-   **「正在试听中」提示隐藏**
-   **收银台拦截**：VIP 购买页直接拦截
-   **鲸云音效、换肤 / 夜间模式**
-   **云盘上传**
-   **自动签到**：启动后自动执行每日签到
-   **设置入口**：内嵌在网易云设置页，标题「云音源 9.5.81 by Frank」

## 构建

环境：

-   JDK 11
-   Gradle 6.9.2（wrapper）
-   Android Gradle Plugin 4.0.2
-   compileSdk 29 / buildTools 29.0.3（打包时也可用 build-tools 34）
-   `app/src/main/jniLibs/arm64-v8a/libnode.so`（41 MB）为 UnblockNeteaseMusic 运行时，构建必需

```bash
# 设置 JDK 11
export JAVA_HOME=/path/to/jdk-11
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/dolby_beta_<versionName>_<date>-release.apk`

模块 APK 需先签名，再作为 Xposed 模块安装使用，或按内嵌方式把 dex / so 合入网易云安装包。

## 目录结构

```
app/src/main/java/com/cloudmusic/source/
├── Hook.java              # 模块入口
├── hook/                  # 各功能 Hook 实现
│   ├── BlackHook.java         # 会员字段改写
│   ├── PlayUrlHook.java       # 播放地址替换
│   ├── DownloadHook.java      # 下载
│   ├── HideVipHintHook.java   # 文案 / 收银台 / 免标签隐藏
│   ├── AutoSignInHook.java    # 自动签到
│   ├── TabHideHook.java       # 底部导航精简
│   └── ...
├── helper/
│   ├── SourceHelper.java      # 自有音源请求与缓存
│   ├── SettingHelper.java     # 设置项存储
│   └── EAPIHelper.java        # EAPI 接口
├── view/                  # 设置界面组件
└── utils/
```

## 已知限制

-   **一起听切歌**：房间级试听限制由服务端控制，房主同样会被切，本地 Hook 无法绕过
-   **音质列表**：可选项由服务端下发，无法本地扩展
-   **SVIP 徽章**：RN 位图渲染，无法替换
-   **微信 / QQ 登录**：腾讯侧校验重打包签名，无法使用
-   **侧边栏**：RN 绝对布局渲染，无法按项精简

## 致谢

-   [nining377/dolby_beta](https://github.com/nining377/dolby_beta)
-   [nondanee/UnblockNeteaseMusic](https://github.com/nondanee/UnblockNeteaseMusic)
-   [UnblockNeteaseMusic/server](https://github.com/UnblockNeteaseMusic/server)
-   [bin456789/Unblock163MusicClient-Xposed](https://github.com/bin456789/Unblock163MusicClient-Xposed)
-   [Flysky12138/UnblockNeteaseMusic-Android](https://github.com/Flysky12138/UnblockNeteaseMusic-Android)

## 许可

The MIT License。仅供学习交流，请勿用于商业用途。
