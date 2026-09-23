# 阅读器（Reader）

一款简洁的 Android 本地 TXT 小说阅读器。

> 🤖 本项目由 **[Claude Code](https://claude.com/claude-code)** 协助生成与开发。

## 功能

- **导入本地 TXT**：系统文件选择器（SAF）打开，自动复制到应用存储目录 `Android/data/com.tianjc.reader/files/books/`，无需存储权限
- **自动章节提取**：识别「第X章/回/节/卷」「序章/楔子/后记」「Chapter X」等章节标题，按字节偏移记录，不拆分文件；无章节时整本兜底为一章
- **编码检测**：自动识别 UTF-8 / GBK / GB18030 / Big5 / UTF-16（juniversalchardet + BOM）
- **两种阅读方式**：左右翻页（覆盖 / 平移 / 无动画）、上下滚动（惯性滚动、跨章衔接）
- **三种色调**：白天 / 护眼（暖黄暖棕）/ 夜晚
- **阅读设置**：字号、行距、边距
- **进度管理**：按实际阅读位置计算全书进度，自动保存恢复；目录快速跳转

---

# 设计方案（回溯文档）

> 记录 2026-09 的初始设计决策与演进结果，供后续迭代参考。

## 1. 技术选型

| 方面 | 方案 | 理由 |
|---|---|---|
| UI | View 体系 + 自定义 `ReaderView`（StaticLayout） | TXT 核心是文字精确分页与翻页手感，自定义 View 性能最好；Compose 做长文本分页反而受限 |
| 架构 | MVVM：ViewModel + StateFlow + Coroutines | 文件复制、编码检测、章节解析均为耗时操作 |
| 数据库 | Room | 书架、章节目录、阅读进度 |
| 文件选择 | SAF（`ACTION_OPEN_DOCUMENT`） | 无需存储权限，Android 11+ 完全兼容 |
| 页面结构 | 单 Activity + Fragment（书架/设置），阅读页独立 Activity | 阅读页全屏沉浸，独立 Activity 便于沉浸式控制 |

## 2. 模块划分

```
com.tianjc.reader
├─ App.kt
├─ data/
│  ├─ db/          AppDatabase, BookDao, ChapterDao
│  ├─ entity/      Book, Chapter
│  ├─ repo/        BookRepository
│  └─ BookStore.kt 管理 books 目录文件（复制/偏移读取/删除）
├─ domain/
│  ├─ importer/    BookImporter, EncodingDetector
│  ├─ parser/      ChapterParser（正则 + 字节偏移）
│  └─ page/        Paginator（StaticLayout 分页）
├─ ui/
│  ├─ bookshelf/   MainActivity, BookshelfFragment, BookshelfViewModel, BookAdapter
│  ├─ reader/      ReaderActivity, ReaderView, ReaderViewModel, ChaptersBottomSheet
│  └─ settings/    SettingsActivity, ReaderSettings
└─ util/           EdgeToEdge（insets）
```

分层职责：`domain`（纯逻辑，不依赖 Android 生命周期）→ `data`（持久化与文件）→ `ui`（展示）。

## 3. 导入与存储流程

```
SAF 文件选择 (content:// URI，临时读权限)
   ▼
复制 → getExternalFilesDir(null)/books/book_{UUID}.txt
       （外部私有目录：零权限、卸载清除、文件管理器可见）
   ▼
读取字节 → BOM 识别 + juniversalchardet 检测编码
   ▼
章节扫描 → 写 Room（books / chapters）→ 出现在书架
```

- 用 UUID 命名，规避中文路径与重名；原始文件名存库。
- 复制失败/解析异常时删除已复制文件，不留垃圾。
- 阅读按字节偏移 `RandomAccess` 式读取章节，**不在导入时拆分文件**。

## 4. 章节提取

- 流式按行扫描字节，同时记录每行**字节起始偏移**（保证偏移可用于文件定位）。
- 内置标题规则：
  - `第 [数字/中文数字] [章节回卷部集] 标题`
  - `序章/序言/楔子/引子/前言/后记/尾声/番外/终章`
  - `Chapter N`（含罗马数字）
  - `卷 N`、单独的`正文`
- 标题行最长 40 字符；相邻标题间距 <500 字节视为假标题过滤（避免「第1条/第2条」污染目录）。
- 有效标题 <2 个 → 全书兜底为一章。

数据库章节只存偏移：

```
Chapter: chapterIndex, title, startByteOffset, byteLength
```

## 5. 分页与阅读引擎

```
章节文本（懒加载：当前章 + 预载相邻章，文本缓存于 ViewModel）
   ▼ TextPaint（字号/行距/边距）+ 屏幕内容区
StaticLayout 逐行排版，按内容高度切页
   ▼
List<IntRange>：每页 = 章内 char start..end（不复制文本）
```

- 翻页时从章节文本 substring 当前页区间绘制；页只存偏移，内存极小。
- **两种模式**：
  - 左右翻页：覆盖 / 平移（整页跟移动画）/ 无动画；点击左/右区域、fling、慢速拖动松手、音量键。
  - 上下滚动：整章一个长 StaticLayout，手指/惯性滚动；在章首/章末继续滑动 → 跨章。
- 页脚显示：左为章节标题，右为 `页码 x/y`（翻页）或 `百分比`（滚动）。
- 设置变化（字号/边距/模式）后保留「章 + 字符偏移」，重新排版并定位。

## 6. 进度模型

- 保存字段：`currentChapterIndex` + `currentCharOffset`（实际可视位置）。
- 全书百分比：

```
percent = (chapterIndex + chapterOffset / chapterLength) / chapterCount × 100
```

  末章读完 → 100。书架卡片直接展示该 `readPercent`。
- 每次翻页/滚动即落库，下次进入凭「章 + 偏移」恢复。

## 7. 色调方案

简化为三态（早期曾有 4 主题 + 亮度方案，已废弃）：

| 模式 | 背景 | 正文 |
|---|---|---|
| 白天 | 白 `#FFFFFF` | 深灰 `#333333` |
| 护眼 | 暖黄 `#F5E8D0` | 暖棕 `#5C4A2E`（整体偏暖，非调亮度） |
| 夜晚 | 近黑 `#121212` | 浅灰 `#9E9E9E` |

## 8. 全屏与刘海

- 全局 edge-to-edge：`setDecorFitsSystemWindows(false)` + `windowLayoutInDisplayCutoutMode = shortEdges`。
- 书架顶部用**占位条**吃掉状态栏/刘海高度（高度由 insets 设置），Toolbar 位于其下，标题与按钮不与系统图标重叠。
- 阅读页沉浸式隐藏系统栏，菜单按 insets 取 padding。

## 9. 构建环境与坑

- Gradle 9.4 / AGP 9.2，**AGP 9 内置 Kotlin 2.2.10 并自动应用**：不要再手动声明 `org.jetbrains.kotlin.android`（带版本 classpath 冲突 / 不带版本 extension 重复）。
- 版本：KSP `2.2.10-2.0.2`（必须 KSP2，KSP1 与 AGP 9 variant API 不兼容）、Room `2.7.2`、core-ktx `1.16.0`（1.19 要求 compileSdk 37）。
- `gradle.properties`：`android.disallowKotlinSourceSets=false`（让 KSP 注册生成目录）。
- 编码检测不用 `android.icu.text.CharsetDetector`（公开 SDK 隐藏，编译失败），用 juniversalchardet。
- DB 版本迁移：v1→v2 增加 `books.readPercent`。

## 10. 环境与构建

- minSdk 24 / targetSdk 36 / JDK 11

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## 11. 后续可选

- 书签、全文搜索
- 自定义章节正则
- EPUB 等更多格式
- 滚动模式跨章提示动画（「下一章」）
