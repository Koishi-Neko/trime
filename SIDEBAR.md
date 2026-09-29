# 键盘左侧栏（Syllable / symbol sidebar）

同文输入法（Trime）的 fork：把 T9/九宫格方案产生的「音节候选」从横向候选条里挪到
键盘左侧的竖直侧栏；同一个侧栏在没有组字时改为铺开一组**点一下就上屏**的符号
（标点／运算符），取代主题左列「单击 4 个 + 长按 4 个」的用法。目标机型 REDMI K80
（arm64-v8a），基于 develop `75844ae`（= 官方 nightly 同一 commit），GPL-3.0。

包名为 `com.osfans.trime.shiyin`，可与官方同文输入法共存安装；应用名显示为「同文·拾音」。

---

## 1. 背景与目标

雾凇 T9 一类方案里，输入数字串（如 `94343`）时候选列表**最前面**会插入若干音节候选
（`zhe` / `xie` / `zhei` …），点选后改写预编辑、继续出词。它们挤占横向候选条的前几位，
影响不需要特选时的选词效率。

本 fork 学百度输入法的做法：

- 音节候选集中到**键盘左侧的竖直侧栏**（候选条下方、覆盖在键盘最左一列符号键之上，
  宽 17% 屏宽）；
- 横向候选条**过滤掉**音节候选，只留正常词候选；
- 点侧栏某项与在候选条里点它**完全等价**（同一 `selectCandidate` 路径、同一全局索引）；
- 音节候选消失（选词 / 删码 / 清空）→ 侧栏隐藏，符号列恢复可点，候选条恢复原样。

侧栏同时承担第二件事：主题左列只有 4 个标点键，另外 4 个要靠长按，既慢又不好发现。
没有组字时侧栏把这 8 个符号直接铺开（可滚动，一屏约 4 行），点一下即上屏；九宫数字
键盘上换成 8 个计算符号。两种内容不会同时出现：有音节候选时音节优先。

---

## 2. 识别规则

librime 通过 JNI 不暴露候选类型，因此按**形状**识别，三层规则同时满足才算音节候选：

| 层 | 规则 | 默认 |
| --- | --- | --- |
| `text` 正则 | 纯小写 ASCII 字母，长度 1–6（`zhe` / `xie` / `die`） | `[a-z]{1,6}` |
| 结构规则 | comment 以 text 本身开头：要么与 text **完全相同**，要么是 **text + `'` + 剩余按键** | — |
| `comment` 正则 | 小写字母开头、只含字母/数字/撇号，剩余部分可有可无 | `[a-z]+('[0-9']+)?` |

两个正则都是**整串匹配**（`Regex.matches`），不是「包含」；结构规则另行判断。
识别不到就当作普通候选：`split()` 原样返回输入列表，侧栏不出现，功能自动失效，不会 crash。

规则实现集中在
`app/src/main/java/com/osfans/trime/ime/candidates/syllable/SyllableCandidateDetector.kt`
一个类里，正则通过构造参数注入，可单测、可替换（设置里的两个正则项只改字符类，
结构规则是格式的固有约束，不随正则覆盖）。

### 续轮 comment 的真实格式（harness 实机数据固定）

lua 译码器把 comment 定义为「这个候选定下来的写法」。用真实手机包 + 仓库 lua 的
无头 harness 逐轮抓取（`tools/harness_real.py` 同款链路），两种形态：

- **带剩余按键**：`音节'剩余数字`（`wo'9436`、`xi'36`、`xie'6`、`di'3`）
- **裸音节**：comment 就是音节本身（`ge`、`he`、`gen`、`die`、`zhen`）——
  当这个音节正好吃掉**整段剩余数字**时没有剩余可报，lua 就直接给裸 comment。
  连续逐字选音节的**每一轮最后一个音节**都是这个形态。

真实数据样例（输入 969436 → 点 wo 后的各轮）：

| 轮次 | input | 侧栏应显示的音节候选（text/comment） |
| --- | --- | --- |
| R1 | `969436` | `wo`/`wo'9436`、`yo`/`yo'9436` |
| R2 | `wo'9436` | `xi`/`xi'36`、`yi`/`yi'36`、`zi`/`zi'36`、`xie`/`xie'6`、`zhe`/`zhe'6`、`zhen`/`zhen`（裸） |
| 续轮尾 | `wo'436` | `ge`/`ge'6`、`he`/`he'6`、`gen`/`gen`（裸）、`hen`/`hen`（裸） |
| 中间态 | `zhe'43` | `ge`/`ge`（裸）、`he`/`he`（裸） |

注意 R2 里既有带数字的也有裸的，**只靠 `[a-z]+'[0-9']+` 会漏掉全部裸音节**
（真机「选完第一个音节后第二字音节不进侧栏」的根因）。裸 comment 与英文补全
comment 同形，靠结构规则（comment 必须等于 text 或以 `text'` 开头）把
`the`/`pron` 这类词性注释挡在外面。

### 为什么不误伤普通候选

- **全拼方案**：正常候选 `text` 是汉字（`这`），第一条就不匹配；
- **带拼读注释的词**：`text` 是汉字、`comment` 含空格（`wo zhen`），comment 正则不匹配；
- **英文补全**：`text` 是字母但 `comment` 为空（不匹配 comment 正则）或是词性注释
  （不满足结构规则）；
- 唯一留下的同形歧义是 `you`/`you`（comment 原样重复单词）：目标方案里没有这种
  词典候选，而且就算误归类，经全局索引选中仍然正确，只影响它显示在侧栏还是候选条。

以上情形都有单测固定（见第 8 节）。手改的规则若不合法（无法编译成正则），
自动回落到内置规则。

---

## 3. 符号侧栏（Symbol sidebar）

### 3.1 显隐与内容（优先级即判定顺序）

| 优先级 | 条件 | 侧栏内容 |
| --- | --- | --- |
| 1 | 有音节候选（第 2 节） | 音节候选列表，行为与加符号集之前**完全一致** |
| 2 | 未组字，且当前是九宫主键盘 | 标点集：`，` `。` `？` `！` `、` `——` `（）` `【】` |
| 3 | 当前是九宫数字键盘 | 运算符集：`+` `-` `*` `/` `=` `_` `（）` `【】` |
| 4 | 组字中但没有音节候选 | 不显示（`GONE`），露出主题左列的 分词/上页/下页/Esc 等功能键 |
| 5 | 其它键盘（`default`、`qwerty` 等 26 键） | 永不显示符号 —— 17% 的叠加会挡住 `q/a/z` |

规则 2 在组字期间让位，因为那时九宫主键盘左列是功能键；规则 3 不附加组字条件，数字键盘
的按键直接上屏、左列本来就不是功能键。判定是一个纯函数
`SidebarContentResolver.resolve()`（输入：音节候选、是否组字、键盘种类、符号开关），
不碰 Android API，单测全覆盖。

判定需要的两个输入是这么拿到的：

- **是否组字**：`rime.run { statusCached.isComposing }`（与 `InputView.broadcastKeyAppearanceUpdate()`
  同源），在候选 / composition / 键盘外观任一广播之后重新求值；
- **当前键盘 id**：主题 yaml `preset_keyboards` 的键名（`luna_jiugong`、`jiugong_number` …）。
  键盘切换不会产生任何 rime 消息（`_keyboard_*` 选项、按键 `select`、开始输入都能切，
  而且 `switchKeyboard()` 本身还要 post 到主线程），所以不能只在广播里读；`KeyboardWindow`
  在 `attachKeyboard()` 把 id 发进一个 `replay = 1` 的 `SharedFlow`（`currentKeyboardId`），
  侧栏订阅它来重新求值。这是本次对 `KeyboardWindow` 的唯一改动（另有私有字段
  `currentKeyboardId` 改名 `keyboardId`，免得与新 flow 重名）。

### 3.2 键盘 id 的判定方式（重要限制）

键盘 id 是**主题数据**，不是固定枚举：仓库里没有任何 `jiugong` 字样（T9 主题在设备上），
所以只能按名字里的标记判定，而且**先排除 26 键类**（排除优先于九宫匹配）：

| 标记 | 作用 |
| --- | --- |
| 26 键排除：含 `letter` / `qwerty` / `26`，或以 `default` 开头 | 一律不显示符号（优先级最高） |
| 九宫标记 `jiugong` / `bihua` / `t9` | 认定是九宫格布局，可以覆盖左列 |
| 数字标记 `number` / `digit` / `numpad`（须先命中九宫标记） | 换成运算符集 |

排除必须优先：主题是拿「回退到哪个键盘」给英文键盘命名的，于是 `default_jiugong_letter`
这种 26 键 id 里带着 `jiugong` 字样。真机上按「中/En」切到英文 26 键后侧栏仍显示、
挡住 `q/a/z` 一列，就是由这条排除规则修掉的（排除按整串判断，`qwerty_jiugong`、
`jiugong_26` 之类同样被挡住）。

于是 `luna_jiugong`、`luna_bihua`、`t9`、`t9_stroke` → 标点集；`jiugong_number` →
运算符集；`default`、`default_jiugong_letter`、`default_symbol`、`qwerty`、`qwerty0`、
`letter`、`number`、`symbols` 等 → 不显示任何符号。主题若用别的命名（例如数字键盘不叫
`jiugong_number`），需要往 `SymbolKeyboardKind` 的 `NINE_KEY_MARKERS` / `NUMBER_MARKERS`
里补一项。

诊断：侧栏每次判定结果变化都会打一行 Debug 日志

```
Sidebar: keyboard=luna_jiugong composing=false entries=8
```

`adb logcat -s Trime` 就能看到实际生效的键盘 id。

### 3.3 符号的提交路径

- 点击某行 → `TrimeInputMethodService.commitText(text)`，也就是
  `InputConnection.commitText(text, 1)`：与键盘按键上屏等价，且不经过 rime，因此九宫
  数字键盘（ascii 模式）里同样正常。
- `（）` / `【】` 成对提交，随后补发一对 `KEYCODE_DPAD_LEFT`（DOWN + UP）把光标移到两个
  半括号之间。不用 `commitText` 的 `newCursorPosition`：框架明说它无法把光标放进所提交
  文本的内部。忽略方向键的编辑器（极少数）会留下光标在右括号之后，文本仍正确上屏。
- 符号集固定写在 `SymbolSets`（`SymbolSpec(text, cursorBack)`），不随方案、主题或输入法
  语言变化；要改符号或改光标回退位数只动这一处。

---

## 4. 开关与配置位置

设置 → **候选窗口**（`AppPrefs.Candidates`）：

| 设置项 | key | 默认 |
| --- | --- | --- |
| 音节侧栏 | `syllable_sidebar` | **开** |
| 符号侧栏 | `symbol_sidebar` | **开** |
| 音节候选的文本规则 | `syllable_text_pattern` | `[a-z]{1,6}` |
| 音节候选的注释规则 | `syllable_comment_pattern` | `[a-z]+('[0-9']+)?` |

关掉「音节侧栏」= 不过滤、不侧栏，行为回到官方版（见第 6 节）。两个规则项只在开关打开
时才可编辑（`enableUiOn`）。

「符号侧栏」与「音节侧栏」互相独立：关掉前者只是不再出现符号集，音节候选照常进侧栏；
关掉后者只是不过滤音节、不显示音节行。两个都关 = 侧栏恒定 `GONE`，主题左列完全恢复。
两个开关都登记在 `TrimeInputMethodService.recreateInputViewPrefs` 里，切换后立刻重建
InputView 生效。

---

## 5. 行为细节

- **位置**：`InputView.keyboardView` 内，约束为 `below(inputBar.view)` + `above(bottomPaddingSpace)`
  + `startOfParent()`，宽度 `matchConstraintPercentWidth = 0.17`（屏宽的 17%，落在要求的 15–18% 区间）。
- **叠加而非占列**：侧栏在 `keyboardView` 里**加在 `windowManager.view`（键盘按键区）之后**，
  因此浮于按键区之上、拦截其 bounds 内的触摸；键盘区的左右边界约束与侧栏关闭时**逐字段一致**
  （`startToEndOf(leftPaddingSpace)`/`endToStartOf(rightPaddingSpace)` 或贴父级两端），
  布局、宽度完全不变。侧栏隐藏（`GONE`）后符号列自然露出、恢复可点。
- **背景不透明**：背景是主题注入的 `candidate_background` + `candidate_border_color`
  （与候选条同一 `decorDrawable`，alpha 默认 255），盖住底下的符号键；行内高亮底色等仍走
  主题色，无硬编码颜色。
- **滚动**：侧栏是竖直 `LinearLayoutManager` 的 `RecyclerView`，行高 = `max(主题
  style.candidateViewHeight, 52dp)`（主题值打底、保证可点），行上下各留 3dp 间距，
  超出可视范围自然可滚动。行内两行文字作为一个整体垂直居中（packed chain），行距 2dp。
- **配色**：全部走主题已注入的颜色，无硬编码——背景 `candidate_background` +
  `candidate_border_color`（与候选条同一 `decorDrawable`），大字号 text 用 `candidateTextColor`
  / `fonts.candidate`，comment 用 `commentTextColor` / `fonts.comment`，被高亮项用
  `hilitedCandidateTextColor` / `hilitedCommentTextColor` / `hilitedCandidateBackColor`。
  换配色方案时 `InputView.refreshColors()` 会重建背景并重绑可见行。
- **字号**：固定 sp 常量——音节 20sp、comment 12sp（`SyllableItemUi` 伴生对象）。
  取值依据：主题默认 `candidateTextSize=15sp` / `commentTextSize=10sp`，在 17% 窄列里
  再经 `AutoScaleTextView` 等比压缩后过小（真机可用性反馈「字太小、点不到」）；
  颜色与字体仍全部走主题注入的 key，字号不因主题变小而失守。不改主题时
  行高 52dp + 字号 20/12sp 实测可轻松点按。
- **字号自适应**：两行文字都用 `AutoScaleTextView`（`Proportional`），音节过长时压缩而不是裁切。
- **符号行**：单行、字号 24sp（`SymbolItemUi.SYMBOL_SIZE_SP`）、垂直居中，两字符的
  `（）`/`【】` 被约束在行宽内等比压缩，不会裁切。符号行没有 comment、也不参与高亮。
  它独占一个 RecyclerView view type（`SidebarEntry.Symbol`），不与音节行共用 ViewHolder：
  `AutoScaleTextView` 只在 `setText` 时重算字形度量，字号必须在这一行创建时就定死
  （见第 11 节）。
- **选中**：`rime.selectCandidate(item.globalIndex, global = true)` —— 与候选条点击同一个调用。
  `globalIndex` 是该候选在**完整候选列表**中的全局序号，所以即使它已被候选条过滤掉也正确。
- **高亮**：`Candidates.Bulk.highlighted` 是全局索引，侧栏逐项比较决定是否高亮；候选条的
  高亮/点击也改为通过同一张全局索引表映射。

---

## 6. 兼容性：关掉开关时与官方版等价

关闭开关时 `SyllableCandidateRules.split()` 立刻返回 `SyllableCandidateSplit.identity(...)`：

- `displayed` 是**传入的同一个数组实例**（不是副本）；
- `displayedGlobalIndices` 是恒等映射 `[0, 1, …, n-1]`；
- `syllables` 为空 → 侧栏 `GONE`。键盘的横向约束不随侧栏变化（叠加方案下与上游逐字段
  一致：`startOfParent`/`endOfParent` 或 `startToEndOf(leftPaddingSpace)`/
  `endToStartOf(rightPaddingSpace)`）；开关关闭时 `split()` 返回恒等切分，候选条不过滤，
  行为与上游一致。

因此 `CompactCandidateDelegate` 交给 adapter 的列表、列表长度、`secondLayoutPassNeeded`、
高亮判定（`globalIndexOf(i) == highlightedIdx`，恒等后退化为 `i == highlightedIdx`）与点击
索引都和改动前一致。这两处是本功能的全部渲染路径改动，可用 `git diff` 逐行核对。

符号侧栏开关关掉后 `SidebarContentResolver` 只返回空列表，侧栏保持 `GONE`，主题左列的
单击与长按符号键完全恢复；两个开关都关时侧栏在任何键盘、任何状态下都不出现。

native 层**一个文件都没改**，`app/src/main/jni/**` 干净；构建走 prebuilt 路径。

---

## 7. 代码改动点地图

音节内容（`app/src/main/java/com/osfans/trime/ime/candidates/syllable/`）：

| 文件 | 作用 |
| --- | --- |
| `SyllableCandidateDetector.kt` | 识别规则本体（可注入正则） |
| `SyllableCandidateSplit.kt` | `SyllableCandidate` 数据类 + 切分逻辑 `of()` / `identity()` |
| `SyllableCandidateRules.kt` | 读偏好、缓存 detector、`split()` 入口 |

符号内容（`app/src/main/java/com/osfans/trime/ime/candidates/symbol/`）：

| 文件 | 作用 |
| --- | --- |
| `SymbolSets.kt` | 两个 8 项符号集 + `SymbolSpec`（提交文本 / 光标回退位数） |
| `SymbolKeyboardKind.kt` | 键盘 id → `NONE` / `PUNCTUATION` / `OPERATORS` 的纯函数判定 |

侧栏视图（`app/src/main/java/com/osfans/trime/ime/candidates/sidebar/`，前 4 个文件由
`syllable/` 包移入并改名）：

| 文件 | 作用 |
| --- | --- |
| `SidebarEntry.kt` | 行模型：`Syllable(text, comment, globalIndex)` / `Symbol(text, cursorBack)` |
| `SidebarContentResolver.kt` | 显隐与内容的纯函数判定（第 3.1 节的优先级表） |
| `SidebarDelegate.kt` | 侧栏 `RecyclerView`、接收候选/组字/键盘广播、显隐与点击（音节→选候选，符号→上屏） |
| `SidebarViewAdapter.kt` | 侧栏 adapter（两种 view type） |
| `SidebarViewHolder.kt` | 侧栏 ViewHolder（`Syllable` / `Symbol`） |
| `SyllableItemUi.kt` | 音节行 UI（大字号 text + comment，原 `SyllableSidebarItemUi`） |
| `SymbolItemUi.kt` | 符号行 UI（单个大字号标签，垂直居中） |

修改：

| 文件 | 改动 |
| --- | --- |
| `ime/candidates/compact/CompactCandidateDelegate.kt` | 用 `SyllableCandidateRules.split()` 过滤候选条；点击/长按改用全局索引 |
| `ime/candidates/compact/CompactCandidateViewAdapter.kt` | 新增 `globalIndices` 与 `globalIndexOf()`；高亮按全局索引判定 |
| `ime/candidates/syllable/` 下的 4 个 UI 文件 | 移到 `ime/candidates/sidebar/` 并改名（`SidebarDelegate` / `SidebarViewAdapter` / `SidebarViewHolder` / `SyllableItemUi`） |
| `ime/core/InputView.kt` | 注册 `SidebarDelegate`；侧栏以叠加方式加在 `windowManager.view` 之后；键盘左右边界保持上游逻辑；`onDetachedFromWindow()` 里 `sidebar.dispose()` 取消键盘订阅 |
| `ime/keyboard/KeyboardWindow.kt` | 新增 `currentKeyboardId` flow（`attachKeyboard()` 里 emit）、私有字段 `currentKeyboardId` 改名 `keyboardId` |
| `ime/core/TrimeInputMethodService.kt` | 开关变化时重建 InputView（音节开关会改变布局；符号开关也走同一条路径以立即生效） |
| `data/prefs/AppPrefs.kt` | 新增 4 个候选窗口偏好 |
| `res/values/input_view_ids.xml` | `syllable_sidebar_view` → `sidebar_view` |
| `res/values/strings.xml`、`values-zh-rCN`、`values-zh-rTW` | 新增 5 条设置文案；应用名改为「同文·拾音」 |
| `app/build.gradle.kts` | `applicationId` → `com.osfans.trime.shiyin`，去掉 debug 的 `.debug` 后缀 |

### applicationId 排查结果

Manifest 里所有与包名相关的项都走 `${applicationId}` 占位符或 `BuildConfig.APPLICATION_ID`，
改一处即自动跟随（已在产出的 APK 上核对）：

- `authorities`：`com.osfans.trime.shiyin.provider`（SAF 文档提供者）、
  `com.osfans.trime.shiyin.fileprovider`、`com.osfans.trime.shiyin.androidx-startup`
- 自定义权限：`com.osfans.trime.shiyin.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
- `util/Intent.kt` 的 documents root URI、`util/InputMethodUtils.kt` 的输入法服务匹配、
  `receiver/RimeIntentReceiver.kt` 的 action、`MainActivity` 的 extra key
- 数据目录：`DataManager` 用 `getExternalFilesDir(null)`，落在
  `Android/data/com.osfans.trime.shiyin/`，与官方互不干扰
- 无 `sharedUserId`；`res/xml/method.xml` 的 `settingsActivity` 是类名
  （`com.osfans.trime.ui.main.MainActivity`，属 `namespace` 而非 `applicationId`），保持不变

---

## 8. 验证

```bash
cd /home/koishi/trime
BUILD_ABI=arm64-v8a ./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

- 构建日志中不出现 `configureCMake` / `buildCMake`（prebuilt native 路径生效）。
- 单测（本次新增 15 例）：`SyllableCandidateDetectorTest`（7 例）、`SyllableCandidateSplitTest`
  （4 例）、`SymbolSetsTest`（4 例）、`SymbolKeyboardKindTest`（4 例）、
  `SidebarContentResolverTest`（7 例）；全部 **366** 个用例通过，0 失败。
- 产物里只有 `lib/arm64-v8a/librime_jni.so` 一个 native 库。

单测覆盖：
- 正例：`[a-z]{1,6}` + 结构规则 + `[a-z]+('[0-9']+)?` 的各种组合，含续轮裸 comment
  （`ge`/`ge`、`gen`/`gen`、`zhen`/`zhen`、`die`/`die`）与带剩余按键（`xi`/`xi'36`）；
- 反例：长度越界、大写、含数字、汉字 text、空 comment、词性注释 `the`/`pron`、
  `zhe'`、`'43`、`ZHE'43`、comment 与 text 不符的 `xie`/`xi'36`；
- `you`/`you` 同形歧义按音节接受（只影响显示位置，不影响选中）；
- 自定义正则生效（字符类层面；结构规则不可覆盖）；
- 切分后 `displayed` 保持原数组实例（等价性）、全局索引映射正确、空列表不炸、
  裸 comment 续轮轮的切分映射；
- 键盘 id 判定：`luna_jiugong` / `luna_bihua` / `t9` / `t9_stroke` → 标点，
  `jiugong_number` / `t9_numpad` → 运算符，`default` / `qwerty` / `qwerty0` / `number` /
  `symbols` / 空串 → 不显示，大小写无关；
- 符号集内容与顺序：各 8 项、`（）` 与 `【】` 的 `cursorBack = 1`、其余为 0；
- 显隐优先级：音节候选优先、九宫主键盘未组字→标点、组字中→空、九宫数字键盘→运算符
  （组字中也显示）、其它键盘→空、开关关掉→空（音节行不受影响）。

---

## 9. 已知限制

1. **只对横向候选条生效**。若在设置里启用「显示候选词窗口」（paging mode）或用系统候选
   视图，候选走 `Paged` 通道而非 `Bulk`，侧栏不出现、也不过候选条。目标场景（九宫格软键盘）
   走的是 `Bulk`。
2. **候选列表上限 16**。JNI 的 `getBulkCandidates()` 只取前 16 项（`limit = 16`）。音节候选
   被排在列表最前面，够用；若某方案把音节放到 16 名之后就看不到。
3. **「展开全部候选」面板不参与过滤**（`FlexboxUnrolledCandidateWindow` 仍会显示音节候选）。
4. **宽度固定 17% 屏宽**，不随主题、横竖屏或候选数量调整；叠加位置固定在键盘最左缘
   （`startOfParent`）。若主题的 `keyboardPadding ≠ 0`，或最左一列不是符号列，叠加处与
   实际符号列会有偏移。
5. **识别是启发式的**。comment 与 text 同形的英文补全（`you`/`you`）会按音节接受；
   方案若用别的格式，需要在设置里改两个正则（字符类层面），或改
   `SyllableCandidateDetector` 的默认值。
6. 侧栏没有做长按「忘记该词」菜单（候选条有）。
7. 已在 REDMI K80 上安装点按验收过一轮（发现「占列压键盘」与「续轮裸 comment 漏检」
   两个问题，即本分支最新的两个修复）；换主题/配色后叠加与配色的表现未逐一验证。
8. **符号侧的键盘判定靠名字标记**（第 3.2 节），排除规则优先：id 含 `letter` /
   `qwerty` / `26` 或以 `default` 开头的一律先排除，其余再按九宫标记判定。这条排除是
   真机验收修出来的——主题的英文 26 键叫 `default_jiugong_letter`，名字里带着
   `jiugong`，切到英文后侧栏挡住 `q/a/z`。反过来也有代价：主题若把**九宫**键盘命名成
   `default_*`（例如 `default_jiugong`），侧栏会被排除掉，需要调整
   `SymbolKeyboardKind.ALPHABET_MARKERS` / `ALPHABET_PREFIX`。用
   `adb logcat -s Trime` 里 `Sidebar: keyboard=...` 一行确认实际 id。
9. **符号集是写死的 8 项**（中文全角标点／算术符号），不随方案、主题或输入法语言变化；
   括号对靠 `KEYCODE_DPAD_LEFT` 定位光标，个别不接受方向键的编辑器会把光标留在右括号之后
   （文本仍正确上屏）。
10. **数字键盘的运算符集不受组字状态影响**（第 3.1 节规则 3）。若实际主题的数字键盘在
   组字时左列是功能键，需要给 `SidebarContentResolver` 里 `OPERATORS` 这一支也加上
   `if (composing) null`。
11. **符号侧栏尚未上机验收**（本次只做了单测 + 构建）。上机需要确认：① 九宫主键盘空闲时
   出现 8 个标点、可滚动、点一下上屏；② 一旦组字侧栏让位，露出主题左列功能键；③ 数字
   键盘出现运算符集，`（）`/`【】` 光标落在括号中间；④ 26 键键盘上侧栏从不出现；
   ⑤ 关掉「符号侧栏」后符号集消失、音节侧栏不受影响；⑥ 符号行的垂直居中（符号行没有
   comment，靠约束居中，未在真机上量过）。

---

## 10. 构建环境备忘

- 免编 native：把官方 nightly APK 里的 `lib/arm64-v8a/librime_jni.so` 放到
  `app/prebuilt/arm64-v8a/`，`NativeBaseConventionPlugin` 检测到 `app/prebuilt` 存在就
  跳过整个 CMake 构建。**HEAD 与 nightly 必须同一 commit**（本仓库都是 `75844ae`），
  否则 JNI ABI 可能对不上。`*.so` 被 `.gitignore` 忽略，不入库。
- `local.properties` 写 `sdk.dir=...`；`~/.gradle/gradle.properties` 写 HTTP/HTTPS 代理。
- 非交互 shell（`wsl.exe bash -c`）不加载 profile，没有 `JAVA_HOME`/`PATH`：
  JDK 在 `~/tools/jdk-21`，SDK 在 `~/tools/android-sdk`。跑构建前先
  `export JAVA_HOME=$HOME/tools/jdk-21` 并给 `PATH` 赋一个**干净的 Linux PATH**
  （继承的 Windows PATH 带空格，会把 `export PATH=...:$PATH` 拆词炸掉）。

---

## 11. 过程中踩到的坑

1. **构建在 configuration 阶段就挂：`Process 'command 'git'' finished with non-zero
   exit value 1`。**
   `ProjectExtensions.runCmd()` 用 `providers.exec` 执行 `git config user.name`，
   而 `providers.exec` 在进程非零退出时直接抛异常，`exitValue == 0` 的判断根本没机会执行。
   WSL 里没有 git 身份 → `git config user.name` 退出码 1 → 构建失败。
   **解法**：`git config --global user.name/user.email`（提交本来也需要）。
   没有改构建脚本，因为这是环境问题而不是代码问题。

2. **没有 root 权限装 JDK/SDK。** `sudo` 需要密码。
   **解法**：全部装进 `$HOME`——Temurin JDK 21 tarball + Android `cmdline-tools`
   （用 `python3 -m zipfile` 解压，因为 `unzip` 没装），SDK 包用
   `sdkmanager --proxy=... --sdk_root=...`。只有 `local.properties` 与
   `~/.gradle/gradle.properties` 两处配置需要写。

3. **`MatchPattern` 之外的 splitties 常量不是顶层常量。**
   `splitties.views.dsl.core.matchParent` / `wrapContent` 是 `View` 的扩展属性，
   只能在有 View 接收者的 builder lambda 里用。在
   `RecyclerView.LayoutParams(matchParent, …)` 这种没有 View 接收者的位置要用
   `ViewGroup.LayoutParams.MATCH_PARENT`。

4. **`apply { }` + 嵌套 lambda 里的 `this` 不指向 apply 的接收者**（Kotlin 解析到了内层
   lambda 的接收者），`this.globalIndexOf(position)` 编译不过。
   **解法**：改用 `also { self -> … }`，在闭包里显式引用 `self`。

5. **两个消费方必须对同一份候选列表得出相同结论。** 候选条和侧栏都收到同一个
   `Candidates.Bulk`，如果各自实现一遍识别会漂移。
   **解法**：识别与切分放在 `SyllableCandidateRules` / `SyllableCandidateSplit` 里，
   两边都调它；同时把切分逻辑做成纯函数以便单测。

6. **过滤后行号不再等于全局候选号。** 候选条的点击与高亮原本直接用 adapter position
   当全局索引（`selectCandidate(position, global = true)`）。
   过滤音节候选后 position 会错位。
   **解法**：`SyllableCandidateSplit` 额外产出 `displayedGlobalIndices`，
   adapter 暴露 `globalIndexOf(position)`，点击/长按/高亮三条路径都走它；
   关闭开关时它是恒等映射，行为与上游一致。

7. **NDK/CMake 下载很慢且会被代理拖住。** 走 prebuilt 路径时其实不需要它们，
   但 `ndkVersion` 在 convention plugin 里是无条件设置的，所以还是装上更稳
   （`stripDebugDebugSymbols` 会用 NDK 的 strip）。

8. **`AutoScaleTextView` 只在 `setText` 时重算字形度量。** `needsMeasureText` 只在
   `setText`（且文本真的变了）与 `onSizeChanged` 里置位，`onMeasure` 用的 `fontMetrics` /
   `textBounds` 都是那时缓存下来的。所以「同一个 ViewHolder 每次 bind 改 `textSize`」会用
   旧度量绘制（高度、压缩比例都不对）。**解法**：符号行用独立的 RecyclerView view type
   （`SidebarEntry.Symbol`）与独立的 UI，字号在 `onCreateViewHolder` 时定死，音节行不参与
   复用。

9. **键盘 id 是主题数据，仓库里查不到。** 仓库自带的 `trime.yaml` / `tongwenfeng.trime.yaml`
   里都没有 `jiugong` 字样（T9 主题在设备上），所以 `SymbolKeyboardKind` 只能按名字里的标记
   匹配，并打一行 logcat，便于拿真机上的实际 id 核对（第 3.2 节）。

10. **Windows 侧的 Read/Grep 工具走 `//wsl$/...` UNC 路径会踩坑**：仓库里
   `app/src/main/assets/shared` 的软链在 UNC 下不可读，ripgrep 直接报错。
   `wsl.exe bash -c '...'` 的参数里也别用 `$?`/`$PATH` 这类变量——WSL 继承的
   Windows PATH 带空格，`export VAR=$PATH` 会被拆词报错，`$?` 也可能被外层
   shell 抢先展开。**解法**：读文件用 WSL 内的 `git grep`/`sed`；给 PATH 赋干净的
   Linux 值；含撇号的 git 提交信息写文件后用 `git commit -F`。本次新增/改动的文件都先用
   编辑器写到 `~/.kimi-work/` 暂存，再用 `cp` 送进仓库，改存量文件用带断言的 python 脚本做
   精确替换（命中数不为 1 就整体不写），避开嵌套引号与 UNC 两个坑。

