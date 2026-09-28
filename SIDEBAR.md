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

- 音节候选集中到**键盘左侧的竖直侧栏**（候选条下方、键盘按键区左边，宽 17% 屏宽）；
- 横向候选条**过滤掉**音节候选，只留正常词候选；
- 点侧栏某项与在候选条里点它**完全等价**（同一 `selectCandidate` 路径、同一全局索引）；
- 音节候选消失（选词 / 删码 / 清空）→ 侧栏隐藏，键盘取回宽度，候选条恢复原样。

---

## 2. 识别规则

librime 通过 JNI 不暴露候选类型，因此按**形状**识别，两条规则同时满足才算音节候选：

| 字段 | 规则 | 默认正则 |
| --- | --- | --- |
| `CandidateProto.text` | 纯小写 ASCII 字母，长度 1–6（`zhe` / `xie` / `die`） | `[a-z]{1,6}` |
| `CandidateProto.comment` | 音节 + 它的按键序列（`zhe'43` / `zhei'3`） | `[a-z]+'[0-9']+` |

两个正则都是**整串匹配**（`Regex.matches`），不是「包含」。识别不到就当作普通候选：
`split()` 原样返回输入列表，侧栏不出现，功能自动失效，不会 crash。

规则实现集中在
`app/src/main/java/com/osfans/trime/ime/candidates/syllable/SyllableCandidateDetector.kt`
一个类里，正则通过构造参数注入，可单测、可替换。

### 为什么不误伤普通候选

- **全拼方案**：正常候选 `text` 是汉字（`这`），第一条就不匹配；
- **英文补全**：`text` 是字母（`the`），但 `comment` 为空或不是 `key'数字`，第二条不匹配；
- 手改的规则若不合法（无法编译成正则），自动回落到内置规则。

以上三种情形都有单测固定（见第 7 节）。

---

## 3. 开关与配置位置

设置 → **候选窗口**（`AppPrefs.Candidates`）：

| 设置项 | key | 默认 |
| --- | --- | --- |
| 音节侧栏 | `syllable_sidebar` | **开** |
| 音节候选的文本规则 | `syllable_text_pattern` | `[a-z]{1,6}` |
| 音节候选的注释规则 | `syllable_comment_pattern` | `[a-z]+'[0-9']+` |

关掉「音节侧栏」= 不过滤、不侧栏，行为回到官方版（见第 5 节）。两个规则项只在开关打开
时才可编辑（`enableUiOn`）。

---

## 4. 行为细节

- **位置**：`InputView.keyboardView` 内，约束为 `below(inputBar.view)` + `above(bottomPaddingSpace)`
  + `startOfParent()`，宽度 `matchConstraintPercentWidth = 0.17`（屏宽的 17%，落在要求的 15–18% 区间）。
- **出现时**：`windowManager.view`（键盘按键区）的左边被约束到侧栏右侧，右边视 `keyboardPadding`
  决定贴边还是让给 `rightPaddingSpace`。侧栏隐藏后约束恢复原状。
- **滚动**：侧栏是竖直 `LinearLayoutManager` 的 `RecyclerView`，行高取主题的
  `style.candidateViewHeight`，超出可视范围自然可滚动。
- **配色**：全部走主题已注入的颜色，无硬编码——背景 `candidate_background` +
  `candidate_border_color`（与候选条同一 `decorDrawable`），大字号 text 用 `candidateTextColor`
  / `fonts.candidate`，comment 用 `commentTextColor` / `fonts.comment`，被高亮项用
  `hilitedCandidateTextColor` / `hilitedCommentTextColor` / `hilitedCandidateBackColor`。
  换配色方案时 `InputView.refreshColors()` 会重建背景并重绑可见行。
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
- `syllables` 为空 → 侧栏 `GONE`，`updateKeyboardHorizontalBounds()` 走进原来的分支，
  键盘约束与改动前逐字段一致（`startOfParent`/`endOfParent`，或
  `startToEndOf(leftPaddingSpace)`/`endToStartOf(rightPaddingSpace)`）。

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
| `SyllableSidebarDelegate.kt` | 侧栏 `RecyclerView`、接收候选广播、显隐通知 |
| `SyllableSidebarViewAdapter.kt` | 侧栏 adapter |
| `SyllableSidebarViewHolder.kt` | 侧栏 ViewHolder |
| `SyllableSidebarItemUi.kt` | 单行 UI（大字号 text + comment） |

修改：

| 文件 | 改动 |
| --- | --- |
| `ime/candidates/compact/CompactCandidateDelegate.kt` | 用 `SyllableCandidateRules.split()` 过滤候选条；点击/长按改用全局索引 |
| `ime/candidates/compact/CompactCandidateViewAdapter.kt` | 新增 `globalIndices` 与 `globalIndexOf()`；高亮按全局索引判定 |
| `ime/core/InputView.kt` | 注册 `SyllableSidebarDelegate`；把侧栏加入 `keyboardView`；抽出 `updateKeyboardHorizontalBounds()` 处理键盘左右边界 |
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
- 单测：`SyllableCandidateDetectorTest`（5 例）、`SyllableCandidateSplitTest`（3 例）为新增；
  其余 28 个既有测试类全部通过。
- 产物里只有 `lib/arm64-v8a/librime_jni.so` 一个 native 库。

单测覆盖：
- 正例：`[a-z]{1,6}` + `[a-z]+'[0-9']+` 的各种组合；
- 反例：长度越界、大写、含数字、汉字 text、空 comment、`you`/`pron` 这类英文补全 comment、
  `zhe'`、`'43`、`ZHE'43`；
- 自定义正则生效；
- 切分后 `displayed` 保持原数组实例（等价性）、全局索引映射正确、空列表不炸。

---

## 8. 已知限制

1. **只对横向候选条生效**。若在设置里启用「显示候选词窗口」（paging mode）或用系统候选
   视图，候选走 `Paged` 通道而非 `Bulk`，侧栏不出现、也不过候选条。目标场景（九宫格软键盘）
   走的是 `Bulk`。
2. **候选列表上限 16**。JNI 的 `getBulkCandidates()` 只取前 16 项（`limit = 16`）。音节候选
   被排在列表最前面，够用；若某方案把音节放到 16 名之后就看不到。
3. **「展开全部候选」面板不参与过滤**（`FlexboxUnrolledCandidateWindow` 仍会显示音节候选）。
4. **宽度固定 17% 屏宽**，不随主题、横竖屏或候选数量调整；出现时键盘按键区确实被压缩，
   没有做叠加式悬浮。
5. **识别是启发式的**。方案若用别的格式（例如 comment 不是 `key'数字`），需要在设置里改
   两个正则，或改 `SyllableCandidateDetector` 的默认值。
6. 侧栏没有做长按「忘记该词」菜单（候选条有）。
7. 只在真机上做过静态与构建验证，**尚未在 REDMI K80 上实际安装点按验证**。

---

## 9. 构建环境备忘

- 免编 native：把官方 nightly APK 里的 `lib/arm64-v8a/librime_jni.so` 放到
  `app/prebuilt/arm64-v8a/`，`NativeBaseConventionPlugin` 检测到 `app/prebuilt` 存在就
  跳过整个 CMake 构建。**HEAD 与 nightly 必须同一 commit**（本仓库都是 `75844ae`），
  否则 JNI ABI 可能对不上。`*.so` 被 `.gitignore` 忽略，不入库。
- `local.properties` 写 `sdk.dir=...`；`~/.gradle/gradle.properties` 写 HTTP/HTTPS 代理。

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

