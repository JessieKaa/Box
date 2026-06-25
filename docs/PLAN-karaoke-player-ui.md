# PLAN — 卡拉OK播放界面优化 (karaoke-player-ui)

## Goal

在卡拉OK **PLAY 模式**的播放器界面：(1) 把顶部歌条从单行静态标题改成横向滚动展示「当前播放 + 下一首」；(2) 在播放器**右上角**新增「手机扫码遥控」二维码入口。

## Context

- 现有播放控制器 `controller_karaoke.xml` 顶部有一条 `llTopBar`（仅 `tvSongTitle` + `tvTrackInfo`，静态 ellipsize=end），看长标题会被截断，且看不出队列下首是谁。
- 同布局右上角已有一个 `llNextUp` 浮层，列出最多 3 首待播歌曲——与本 feature 的「顶部滚动当前+下一首」语义重叠，需要让位给 QR 码或合并。
- SELECT 模式里已有现成的 QR 实现：`KaraokeActivity.generateQRCode()` 用 `ControlManager.get().getAddress(false) + "karaoke"` 作 URL，`QRCodeGen.generateBitmap(...)` 生成位图。PLAY 模式要复用同一 URL。
- PLAY 模式下播放器实际不显示 SELECT 布局里的 `ivQRCode`（它在 select 层 FrameLayout 内），因此 PLAY 顶层需要新建一条专门承载 QR 的容器。
- 项目已用 `androidx` + DataBinding，控件尺寸遵循 `@dimen/vs_*`，文字用 `@dimen/ts_*`，遵循 1280×720 dp 横屏规范。

## Files

预测改动文件（不确定处标 `TBD`）：

- `app/src/main/res/layout/controller_karaoke.xml`
  - 改造 `llTopBar` 内的 `tvSongTitle`：改用 `TextView` + `android:ellipsize="marquee"` + `android:focusable="true"` + `android:focusableInTouchMode="true"` + `android:singleLine="true"` + `android:marqueeRepeatLimit="marquee_forever"`；或新增一条 `tvSongTitleMarquee` 与 `tvNextSongMarquee` 同级，拼接 "当前：《xxx》   下一首：《yyy》" 一并滚动。**推荐方案**：把 `tvSongTitle` 升级成 marquee，文本结构 `当前播放：{title}　　下一首：{nextTitle}`。
  - 右上角：把现有 `llNextUp` **隐藏（GONE）**，在它原位置（`layout_gravity="right|top"`）新建 `llPlayerQR`（半透明黑底）包含一个 `ImageView`（id `ivPlayerQR`，约 `vs_120`）+ 一个 TextView "手机扫码遥控"。**QR 容器要在 `setPlayMode`/`setSelectMode` 时与 `llTopBar` 同步显示/隐藏。**
- `app/src/main/java/com/github/tvbox/osc/karaoke/controller/KaraokeController.java`
  - 新增字段：`private ImageView ivPlayerQR;` + `private LinearLayout llPlayerQR;`
  - `onFinishInflate`/`init` 里 `findViewById` 绑定。
  - 新增 `public void setPlayerQR(Bitmap bitmap)`（透传 `setImageBitmap`）与 `public void setPlayerQRVisible(boolean)`，与 `llTopBar` 同步显示。
  - 新增 `public void setMarqueeText(String currentTitle, String nextTitle)`：拼成 `当前播放：{current}　　下一首：{next}`，写到 `tvSongTitle`，并 `setSelected(true)` 触发 marquee。
- `app/src/main/java/com/github/tvbox/osc/karaoke/KaraokeActivity.java`
  - 复用 `generateQRCode()` 逻辑：抽出 `private Bitmap buildKaraokeQRBitmap()`（不直接 setImageBitmap，返回 Bitmap）。
  - 进入 PLAY 模式时（`enterPlayMode`/`startPlay`）调用 `mController.setPlayerQR(buildKaraokeQRBitmap())` 并 `mController.setPlayerQRVisible(true)`；进入 SELECT 模式时 `setPlayerQRVisible(false)`。
  - 现有 `pushNextUpToController()`/`pushTitleToController()` 附近调用 `mController.setMarqueeText(currentTitle, nextTitle)`——next 取队列 `currentQueueIndex+1` 处元素，无下一首时文案改 "（已到最后一首）"。
- `app/src/main/res/values/strings.xml`（`app/src/main/res/values-zh/strings.xml` 若有）：新增 `karaoke_now_playing_marquee`、`karaoke_no_next_song`、`karaoke_qr_remote_label` 字符串（默认中文）。

## Steps

1. **布局先行**：改 `controller_karaoke.xml`：
   - `llTopBar.tvSongTitle` 加 marquee 属性。
   - 新增 `llPlayerQR`（top|end，与原 `llNextUp` 同位但优先级更高），含 `ivPlayerQR` + label。
   - 保留 `llNextUp` 但默认 `visibility="gone"`（短期内不再使用，避免和 marquee 信息冲突）。
2. **Controller 接口**：在 `KaraokeController.java` 增加 `ivPlayerQR`/`llPlayerQR` 字段、`setPlayerQR(Bitmap)`、`setPlayerQRVisible(boolean)`、`setMarqueeText(current,next)` 方法。`show()`/`hide()` 流程要保证 QR 与 TopBar 同步显隐。
3. **Activity 侧**：
   - 重构 `generateQRCode()`：抽出 `buildKaraokeQRBitmap()` 返回 Bitmap。
   - 在 `enterPlayMode()` / `startPlay(...)` / `updateNowPlayingUI(...)`（具体方法名以现有代码为准，按 grep 结果定）调用 `mController.setPlayerQR(buildKaraokeQRBitmap())`、`setPlayerQRVisible(true)`、`setMarqueeText(currentTitle, nextTitle)`。
   - 在 `enterSelectMode()` 里 `setPlayerQRVisible(false)`。
4. **Marquee 数据源**：找到现有 `pushNextUpToController()`/`updatePlayingTitle()`（具体名以代码为准），让它在每次切歌/队列变化时同步调用 `setMarqueeText`。
5. **资源**：补 strings.xml 三条文案（中英文）。
6. **构建**：在 worktree 内 `./gradlew assembleArm64GenericNormalDebug` 验证可编译；布局改动后再次启动 app，进入卡拉OK → 播放任一首歌，**目视**确认：
   - 顶部条文字滚动连续、长标题不被截断；
   - 右上角显示 QR；
   - 用手机扫 QR 浏览器打开 `http://<局域网IP>:9978/karaoke`，能进遥控页（验证 URL 与 RemoteServer 一致）。

## Acceptance

进入卡拉OK PLAY 模式后必须满足：

- [ ] 顶部条出现连续滚动文字，格式 `当前播放：{当前}　　下一首：{下一首}`；长标题（≥30 字）能完整滚完。
- [ ] 队列只剩 1 首时，文案为 `当前播放：{当前}　　下一首：（已到最后一首）`。
- [ ] 右上角出现约 120dp 见方 QR 码；手机扫描后能打开 `http://<IP>:9978/karaoke`。
- [ ] 切到 SELECT 模式时 QR 与顶部条一起隐藏；回到 PLAY 模式时正确恢复。
- [ ] `./gradlew assembleArm64GenericNormalDebug` 通过。
- [ ] Codex reviewer 一轮通过（最多 2 轮）；评审意见若涉及 marquee 性能、TV focus（QR 不可聚焦）需响应。

## Notes for the worker

- **禁止动 SELECT 模式已有的 `ivQRCode`/`llQRCode`**（那是选歌界面的旧入口，本 feature 不动）。
- **`llPlayerQR` 必须 `focusable="false"`**，否则 D-pad 会聚焦到 QR 上，破坏现有导航。
- marquee 滚动要求 TextView 处于 `setSelected(true)` 状态，且 `focusable=true` 或其父容器取得焦点；如不滚动，是 `setSelected` 没设——这是 Android 老坑。
- 如果 RemoteServer 未启动（ControlManager 未 init），`getAddress(false)` 可能返回空——`buildKaraokeQRBitmap()` 返回 null 时 `setImageBitmap(null)` + 容器 GONE 即可，不要崩。
- strings.xml 路径：`app/src/main/res/values/strings.xml`（默认中文）+ `app/src/main/res/values-zh-rCN/strings.xml`（若存在则同步）。
