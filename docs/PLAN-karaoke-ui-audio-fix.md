# PLAN — 卡拉OK UI 修复:audio-only 模式 marquee/QR 不可见 (karaoke-ui-audio-fix)

## Goal

修复 feature karaoke-player-ui 的遗留 bug:在 audio-only 歌曲(mVideoView 被 setVisibility(GONE))下,顶部 marquee 和右上角 QR 码不显示。把承载这两个 UI 的容器从 `controller_karaoke.xml` 上提到 `activity_karaoke.xml`,与 `karaokeFullLyricView` 同层,由 KaraokeActivity 直接管理可见性。

## Context

- 现状(主仓 HEAD = 6486e2b,已合并 feature/karaoke-player-ui):
  - `controller_karaoke.xml` 里有 `llTopBar`(含 marquee 化的 `tvSongTitle` + `tvTrackInfo`)和 `llPlayerQR`(含 `ivPlayerQR` + 标签)。
  - `KaraokeController` 暴露 `setMarqueeText(current, next)` / `setPlayerQR(Bitmap)` / `setPlayerQRVisible(boolean)`,在 `show()` / `hide()` 里同步显隐。
  - `KaraokeActivity.enterPlayMode` 调 `mController.setPlayerQR(...)` + `setPlayerQRVisible(true)`;`pushNextUpToController` 调 `mController.setMarqueeText(...)`。
- 问题:
  - DKPlayer 的 `mVideoView.setVideoController(mController)` 把 controller 挂为 mVideoView 子节点。
  - `applyAudioOnlyUi(true)` → `mVideoView.setVisibility(View.GONE)` → 整个 controller 子树(含 llTopBar/llPlayerQR)被裁到 0x0,UI 不显示。
  - 同样的根因此前已经把 `KaraokeFullLyricView` 搬出 controller(`activity_karaoke.xml:25` 注释为证),本次只是把 marquee/QR 也按同样方式搬一次。
- 真机证据(TYH201H, 稻香 audio-only):
  - `agent-device screenshot` 后像素扫描:顶部 y=0-100 无文本簇,右上角 (1380-1600, 30-280) 0 个亮像素。
  - DPAD_CENTER 仍能触发 togglePlayPause + Toast,DPAD_UP 仍能触发 switchAudioTrack + Toast — 说明 PLAY 模式逻辑正常,只是 controller 视图不可见。
- 卡拉OK 场景下 audio-only 是主路径(本地 mp3、远端 mediaType=song),该 bug 影响核心体验。

## Files

预测改动:

- `app/src/main/res/layout/controller_karaoke.xml`
  - 删除 `llTopBar`(及其子 `tvSongTitle` / `tvTrackInfo`)。
  - 删除 `llPlayerQR`(及其子 `ivPlayerQR` / 标签 TextView)。
  - 删除 `llNextUp`(已在前一 feature 强制 GONE,本次彻底移除以简化布局)。
  - 保留:`pbLoading` / `llSeekOverlay` / `llBottomBar`(底部控制条)等其余控制器视图。
- `app/src/main/res/layout/activity_karaoke.xml`
  - 在 `mVideoView` 之上、`karaokeFullLyricView` 同级或之上,新增三条:
    1. `llTopBar`(原样搬过来)— 横向 LinearLayout,top|start,半透明黑底,内含 `tvSongTitle`(marquee 属性全保留)+ `tvTrackInfo`。
    2. `llPlayerQR`(原样搬过来)— 竖向 LinearLayout,top|end,半透明黑底,内含 `ivPlayerQR`(vs_120)+ 标签 TextView,`focusable=false`。
  - 这俩容器在 `llSelectLayer` 之下(z-order 顺序:`bgCarouselView` < `mVideoView` < `karaokeFullLyricView` < **`llTopBar`** < **`llPlayerQR`** < `llSelectLayer`)。理由:PLAY 模式下要看得见,SELECT 模式下被 `llSelectLayer` 盖住没关系(后者此时 visibility=GONE 也行,只要由 activity 控制即可)。
  - 默认 `visibility="gone"`,由 activity 在 `enterPlayMode` 显式打开。
- `app/src/main/java/com/github/tvbox/osc/karaoke/KaraokeActivity.java`
  - 新增字段:`LinearLayout llTopBar;` `TextView tvSongTitle;` `TextView tvTrackInfo;` `LinearLayout llPlayerQR;` `ImageView ivPlayerQR;` `Bitmap playerQRBitmap;` `String lastMarqueeCurrent;` `String lastMarqueeNext;` `boolean playerQRVisible;` `boolean marqueeOn;`
  - `initView` / `onCreate` 之后:`findViewById` 绑定上述视图。
  - 抽出 `updateMarqueeUI()`:根据 `currentPlayingSong` + 队列下一首 + `marqueeOn`,决定 `llTopBar` 显隐 + 文本。复用前 feature 的拼接逻辑 `当前播放：{current}　　下一首：{next}`,无下一首时填 `(已到最后一首)`。
  - 抽出 `updatePlayerQRUI()`:根据 `playerQRVisible` + `playerQRBitmap != null`,决定 `llPlayerQR` 显隐。
  - `enterPlayMode(song)`:设 `marqueeOn=true; playerQRVisible=qrBitmap!=null;`,然后 `updateMarqueeUI()` + `updatePlayerQRUI()`。
  - `enterSelectMode()`:设 `marqueeOn=false; playerQRVisible=false;`,然后 update 两个 UI(都隐藏)。
  - `pushNextUpToController`(或同等位置):不再调 `mController.setMarqueeText(...)`,改调自身 `updateMarqueeUI()`。
  - `buildKaraokeQRBitmap()`:保留;返回 Bitmap 存到 `playerQRBitmap`,再 `updatePlayerQRUI()`。
  - `applyAudioOnlyUi(boolean)`:不动 mVideoView 的 setVisibility 逻辑(保持原行为),但 **不需要额外动作**,因为新位置(view 在 activity 层)与 mVideoView 是否可见无关。
- `app/src/main/java/com/github/tvbox/osc/karaoke/controller/KaraokeController.java`
  - 删除字段:`llTopBar` `tvSongTitle` `tvTrackInfo` `llPlayerQR` `ivPlayerQR` `playerQRVisible` `lastMarqueeText` 等所有与 marquee/QR 相关的字段。
  - 删除方法:`setMarqueeText(...)` `setPlayerQR(Bitmap)` `setPlayerQRVisible(boolean)` `setSongTitle(String)`(如果只服务 marquee,删;若服务别处,留)+ 相关 `setTrackInfo`。
  - `show()` / `hide()`:移除对 `llTopBar` / `llPlayerQR` / `llNextUp` 的 setVisibility 引用。保留 `llBottomBar` / seek overlay / loading 等其余逻辑。
  - `onFinishInflate` / init:删除上述视图的 findViewById 绑定。
  - `hide()` 末尾对 `simSlideStart` 的 seek-commit 保留。

## Steps

1. **布局迁移**:`controller_karaoke.xml` 删 `llTopBar` + `llPlayerQR` + `llNextUp`;`activity_karaoke.xml` 加这三个容器(默认 gone)。先确保编译通过(布局引用断链)。
2. **Activity 字段绑定**:在 `KaraokeActivity.onCreate`/`initView` 加 findViewById。
3. **方法迁移**:把 `setMarqueeText` 的逻辑搬到 activity 的 `updateMarqueeUI`,`setPlayerQR` + `setPlayerQRVisible` 搬到 `updatePlayerQRUI`。`enterPlayMode` / `enterSelectMode` 改调本类方法。
4. **清理 controller**:删除 controller 里相关字段和方法,确保 controller 编译通过。
5. **z-order 检查**:`activity_karaoke.xml` 里 `llTopBar` 和 `llPlayerQR` 必须在 `mVideoView` 之上、`llSelectLayer` 之下。`mVideoView` GONE 时这两个仍可见。
6. **手测**:
   - 启动 app 进卡拉OK,选 audio-only 歌(如本地 mp3 或远端 mediaType=song),进 PLAY 模式。
   - 验证:顶部 marquee 滚动显示 "当前播放：xxx　　下一首：yyy";右上角出现 QR 码;扫 QR 能打开遥控页(`http://<IP>:9978/karaoke`)。
   - 按 BACK 回 SELECT,两个 UI 都隐藏;再进 PLAY 恢复。
   - 测 video MV 场景(若有):顶部 marquee + QR 也应可见。
7. **编译**:`./gradlew assembleArm64GenericNormalDebug` 通过。
8. **Codex review**:至少 1 轮,关注 z-order 是否被 llSelectLayer 盖住、SELECT 模式下是否泄漏。

## Acceptance

PLAY 模式下:

- [ ] **audio-only 歌**(mVideoView GONE):顶部 marquee 可见且滚动,右上角 QR 可见。这是修复核心。
- [ ] **video MV**(mVideoView VISIBLE):顶部 marquee 可见且滚动,右上角 QR 可见(不被视频画面盖住)。
- [ ] SELECT 模式:两个 UI 都隐藏。
- [ ] `KaraokeController` 不再持有 `llTopBar` / `llPlayerQR` / `setMarqueeText` / `setPlayerQR*` 相关代码。
- [ ] `./gradlew assembleArm64GenericNormalDebug` 通过。
- [ ] Codex reviewer 一轮通过(最多 2 轮)。

## Notes for the worker

- **不要动 `applyAudioOnlyUi` 的 mVideoView 显隐逻辑** — 那是DKPlayer 控制音频解码的必要路径,改了可能引发 player surface 渲染异常。修复点是让 UI 不再依赖 mVideoView 可见。
- **z-order 是关键**:`activity_karaoke.xml` 是 FrameLayout,子节点 XML 顺序 = z-order(后写的在上)。建议:`bgCarouselView` → `mVideoView` → `karaokeFullLyricView` → `llTopBar` → `llPlayerQR` → `llSelectLayer`。
- **marquee 滚动** 需要 `tvSongTitle.setSelected(true)` 才能起跑 — 在 `updateMarqueeUI` 里每次刷新文本后调一次。
- **QR Bitmap 为 null** 时 `llPlayerQR` 应 GONE,不要画空图。
- **测试数据**:本地曲库至少有 1 首 audio-only(mp3);远端 `mediaType=song` 也行。video MV 可能没有,本次不强测 video,但静态分析要保证 video 模式下也能显示(因为 mVideoView VISIBLE 时 FrameLayout 子节点都可见)。
- **Decision Log**:本次决策 = 选项 A(上提到 activity 层),由 supervisor 在卡点上报后裁决。在 PLAN 末尾加 `## Decision Log` 一行。
