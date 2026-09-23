# 阅读器（Reader）

一款简洁的 Android 本地 TXT 小说阅读器。

## 功能

- **导入本地 TXT**：通过系统文件选择器打开，自动复制到应用存储目录（`Android/data/com.tianjc.reader/files/books/`），无需存储权限
- **自动章节提取**：识别「第X章/回/节/卷」「序章/楔子/后记」「Chapter X」等章节标题，按字节偏移记录，无需拆分文件；无章节时整本兜底
- **编码检测**：自动识别 UTF-8 / GBK / GB18030 / Big5 / UTF-16
- **两种阅读方式**：左右翻页（覆盖 / 平移 / 无动画）、上下滚动（惯性滚动、跨章衔接）
- **三种色调**：白天 / 护眼（暖黄暖棕）/ 夜晚
- **阅读设置**：字号、行距、边距可调
- **进度管理**：按实际阅读位置计算全书进度，自动保存恢复；章节目录快速跳转

## 技术栈

- Kotlin · View 体系 + 自定义 ReaderView（StaticLayout 分页绘制）
- MVVM：ViewModel + StateFlow + Coroutines
- Room（书架、章节目录、阅读进度）
- Edge-to-edge，适配刘海屏
- AGP 9 内置 Kotlin

## 环境

- minSdk 24 / targetSdk 36
- Gradle 9.4 / AGP 9.2 / JDK 11

## 构建

```bash
./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`
