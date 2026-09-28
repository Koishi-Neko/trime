# 音节候选侧栏（Syllable candidate sidebar）

同文输入法（Trime）的 fork，把 T9/九宫格方案产生的「音节候选」从横向候选条里挪到
键盘左侧的竖直侧栏。目标机型 REDMI K80（arm64-v8a），基于 develop `75844ae`
（= 官方 nightly 同一 commit），GPL-3.0。

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

以上情形都有单测固定（见第 7 节）。手改的规则若不合法（无法编译成正则），
自动回落到内置规则。

---

## 3. 开关与配置位置

设置 → **候选窗口**（`AppPrefs.Candidates`）：

| 设置项 | key | 默认 |
| --- | --- | --- |
| 音节侧栏 | `syllable_sidebar` | **开** |
| 音节候选的文本规则 | `syllable_text_pattern` | `[a-z]{1,6}` |
| 音节候选的注释规则 | `syllable_comment_pattern` | `[a-z]+('[0-9']+)?` |

关掉「音节侧栏」= 不过滤、不侧栏，行为回到官方版（见第 5 节）。两个规则项只在开关打开
时才可编辑（`enableUiOn`）。

---

## 4. 行为细节

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
- **字号**：固定 sp 常量——音节 20sp、comment 12sp（`SyllableSidebarItemUi` 伴生对象）。
  取值依据：主题默认 `candidateTextSize=15sp` / `commentTextSize=10sp`，在 17% 窄列里
  再经 `AutoScaleTextView` 等比压缩后过小（真机可用性反馈「字太小、点不到」）；
  颜色与字体仍全部走主题注入的 key，字号不因主题变小而失守。不改主题时
  行高 52dp + 字号 20/12sp 实测可轻松点按。
- **字号自适应**：两行文字都用 `AutoScaleTextView`（`Proportional`），音节过长时压缩而不是裁切。
- **选中**：`rime.selectCandidate(item.globalIndex, global = true)` —— 与候选条点击同一个调用。
  `globalIndex` 是该候选在**完整候选列表**中的全局序号，所以即使它已被候选条过滤掉也正确。
- **高亮**：`Candidates.Bulk.highlighted` 是全局索引，侧栏逐项比较决定是否高亮；候选条的
  高亮/点击也改为通过同一张全局索引表映射。

---

## 5. 兼容性：关掉开关时与官方版等价

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

native 层**一个文件都没改**，`app/src/main/jni/**` 干净；构建走 prebuilt 路径。

---

## 6. 代码改动点地图

新增（`app/src/main/java/com/osfans/trime/ime/candidates/syllable/`）：

| 文件 | 作用 |
| --- | --- |
| `SyllableCandidateDetector.kt` | 识别规则本体（可注入正则） |
| `SyllableCandidateSplit.kt` | `SyllableCandidate` 数据类 + 切分逻辑 `of()` / `identity()` |
| `SyllableCandidateRules.kt` | 读偏好、缓存 detector、`split()` 入口 |
| `SyllableSidebarDelegate.kt` | 侧栏 `RecyclerView`、接收候选广播、显隐（叠加时拦截列内触摸） |
| `SyllableSidebarViewAdapter.kt` | 侧栏 adapter |
| `SyllableSidebarViewHolder.kt` | 侧栏 ViewHolder |
| `SyllableSidebarItemUi.kt` | 单行 UI（大字号 text + comment） |

修改：

| 文件 | 改动 |
| --- | --- |
| `ime/candidates/compact/CompactCandidateDelegate.kt` | 用 `SyllableCandidateRules.split()` 过滤候选条；点击/长按改用全局索引 |
| `ime/candidates/compact/CompactCandidateViewAdapter.kt` | 新增 `globalIndices` 与 `globalIndexOf()`；高亮按全局索引判定 |
| `ime/core/InputView.kt` | 注册 `SyllableSidebarDelegate`；侧栏以叠加方式加在 `windowManager.view` 之后；键盘左右边界保持上游逻辑 |
| `ime/core/TrimeInputMethodService.kt` | 开关变化时重建 InputView（侧栏会改变布局，需重建） |
| `data/prefs/AppPrefs.kt` | 新增 3 个候选窗口偏好 |
| `res/values/input_view_ids.xml` | 新增 `syllable_sidebar_view` |
| `res/values/strings.xml`、`values-zh-rCN`、`values-zh-rTW` | 新增 4 条设置文案；应用名改为「同文·拾音」 |
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

## 7. 验证

```bash
cd /home/koishi/trime
BUILD_ABI=arm64-v8a ./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

- 构建日志中不出现 `configureCMake` / `buildCMake`（prebuilt native 路径生效）。
- 单测：`SyllableCandidateDetectorTest`（7 例）、`SyllableCandidateSplitTest`（4 例）为新增；
  全部 351 个用例通过，0 失败。
- 产物里只有 `lib/arm64-v8a/librime_jni.so` 一个 native 库。

单测覆盖：
- 正例：`[a-z]{1,6}` + 结构规则 + `[a-z]+('[0-9']+)?` 的各种组合，含续轮裸 comment
  （`ge`/`ge`、`gen`/`gen`、`zhen`/`zhen`、`die`/`die`）与带剩余按键（`xi`/`xi'36`）；
- 反例：长度越界、大写、含数字、汉字 text、空 comment、词性注释 `the`/`pron`、
  `zhe'`、`'43`、`ZHE'43`、comment 与 text 不符的 `xie`/`xi'36`；
- `you`/`you` 同形歧义按音节接受（只影响显示位置，不影响选中）；
- 自定义正则生效（字符类层面；结构规则不可覆盖）；
- 切分后 `displayed` 保持原数组实例（等价性）、全局索引映射正确、空列表不炸、
  裸 comment 续轮轮的切分映射。

---

## 8. 已知限制

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

---

## 9. 构建环境备忘

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

## 10. 过程中踩到的坑

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

8. **Windows 侧的 Read/Grep 工具走 `//wsl$/...` UNC 路径会踩坑**：仓库里
   `app/src/main/assets/shared` 的软链在 UNC 下不可读，ripgrep 直接报错。
   `wsl.exe bash -c '...'` 的参数里也别用 `$?`/`$PATH` 这类变量——WSL 继承的
   Windows PATH 带空格，`export VAR=$PATH` 会被拆词报错，`$?` 也可能被外层
   shell 抢先展开。**解法**：读文件用 WSL 内的 `git grep`/`sed`；给 PATH 赋干净的
   Linux 值；含撇号的 git 提交信息写文件后用 `git commit -F`。

