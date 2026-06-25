# PLAN — 卡拉OK marquee/QR 周期显隐 + 暂停强显 (karaoke-marquee-qr-cycle)

## Goal

PLAY 模式下,顶部 marquee 条(`llTopBar`)和右上角 QR 码(`llPlayerQR`)按 **8 秒显 / 7 秒隐** 的周期循环切换可见性;暂停播放时**强制常显**;SELECT 模式维持现状(始终隐藏)。用户按任何 DPAD 键时立即重置周期(从 8s 显开始)。

## Context

- 上一个修复(feature/karaoke-ui-audio-fix, 已合并 `b44b7d7`) 把 `llTopBar` + `llPlayerQR` 从 `controller_karaoke.xml` 上提到 `activity_karaoke.xml`,由 `KaraokeActivity` 直接管理显隐(`updateMarqueeUI` / `updatePlayerQRUI`)。
- 当前行为:PLAY 模式下 marquee 和 QR 一直显示。在 audio-only 歌曲长时间播放时,这两个 UI 始终挡在顶部 + 右上角,对画面背景/歌词视觉干扰较大;扫码需求又是间歇的。
- 用户希望减少视觉干扰,但保留"扫码可触发"和"暂停时看歌名"的可用性。

## Files

仅一个文件:`app/src/main/java/com/github/tvbox/osc/karaoke/KaraokeActivity.java`(下文行号基于 worktree HEAD)。

### 新增字段 / 常量(插入到 ~line 164 `marqueeOn` 字段附近)

```java
// marquee/QR 周期显隐 — PLAY 模式下 8s 显 / 7s 隐 循环;暂停强显;DPAD 按键重置。
// 复用 mainHandler 做 scheduling,不加新 Handler 字段。
private static final long CYCLE_SHOW_MS = 8000L;
private static final long CYCLE_HIDE_MS = 7000L;
private boolean marqueeCycleActive = false;   // 防 enterPlayMode 多次重启
private boolean pausedForcesShow = false;     // 暂停态强显标记
// 互引用 Runnable 用匿名内部类 —— lambda 会被 JLS §8.3.3 当作简单名前向引用拒绝
// (字段 A 的初始化器里简单名引用字段 B,B 在 A 之后 —— 编译错)。匿名内部类的 run() 体
// 是独立方法作用域,字段解析在 run() 触发时,此时所有字段已就绪,无前向引用问题。
private final Runnable cycleHideRunnable = new Runnable() {
    @Override
    public void run() {
        hideMarqueeUI();
        mainHandler.postDelayed(cycleShowRunnable, CYCLE_HIDE_MS);
    }
};
private final Runnable cycleShowRunnable = new Runnable() {
    @Override
    public void run() {
        showMarqueeUI();
        mainHandler.postDelayed(cycleHideRunnable, CYCLE_SHOW_MS);
    }
};
```

(修正了 dispatcher 原稿里 `cycleHideRunnable` 不 schedule 下一次 show 的 bug —— 那样 7s 隐后不会再亮。)

### 新增方法(插入到 `updatePlayerQRUI` 之后,~line 734)

```java
/** PLAY 模式下恢复 marquee + QR 显隐(不重画文本/bitmap —— `updateMarqueeUI` / `updatePlayerQRUI` 已缓存)。 */
private void showMarqueeUI() {
    if (currentMode != Mode.PLAY) return;
    if (marqueeOn && llTopBar != null) llTopBar.setVisibility(View.VISIBLE);
    if (playerQRVisible && playerQRBitmap != null && llPlayerQR != null) {
        llPlayerQR.setVisibility(View.VISIBLE);
    }
}

/** 仅切 view,不动 `marqueeOn` / `playerQRVisible` 业务态。 */
private void hideMarqueeUI() {
    if (llTopBar != null) llTopBar.setVisibility(View.GONE);
    if (llPlayerQR != null) llPlayerQR.setVisibility(View.GONE);
}

/** 启动周期;幂等。若已 active 直接 return,避免双发。 */
private void startMarqueeCycle() {
    if (marqueeCycleActive) return;
    marqueeCycleActive = true;
    mainHandler.removeCallbacks(cycleShowRunnable);
    mainHandler.removeCallbacks(cycleHideRunnable);
    showMarqueeUI();
    mainHandler.postDelayed(cycleHideRunnable, CYCLE_SHOW_MS);  // 8s 后进入 hide 相位
}

/** 停周期并强制 hide。用于 SELECT 切换 / 销毁。 */
private void stopMarqueeCycle() {
    mainHandler.removeCallbacks(cycleShowRunnable);
    mainHandler.removeCallbacks(cycleHideRunnable);
    marqueeCycleActive = false;
    pausedForcesShow = false;
    hideMarqueeUI();
}

/** 按键中断 / 切歌时重置:从 show 相位重新开始 8s 倒计时。暂停态下不动(强显优先)。 */
private void resetMarqueeCycle() {
    if (pausedForcesShow) return;
    if (!marqueeCycleActive) return;  // SELECT 模式或未启动时不重置
    mainHandler.removeCallbacks(cycleShowRunnable);
    mainHandler.removeCallbacks(cycleHideRunnable);
    showMarqueeUI();
    mainHandler.postDelayed(cycleHideRunnable, CYCLE_SHOW_MS);
}
```

### 修改点

| # | 方法 (line) | 改动 |
|---|---|---|
| 1 | `enterPlayMode` resume 分支 (line 851 return 前) | 加 `startMarqueeCycle();` |
| 2 | `enterPlayMode` fall-through 末尾 (line 865 / 866 `}` 前) | 加 `startMarqueeCycle();` |
| 3 | `enterSelectMode` line 794 `updateMarqueeUI(null, null);` 之后 | 加 `stopMarqueeCycle();` |
| 4 | `onPlayStateChanged` STATE_PAUSED/STATE_IDLE 分支 (line 286-289) | 进分支第一行:`pausedForcesShow = true; mainHandler.removeCallbacks(cycleShowRunnable); mainHandler.removeCallbacks(cycleHideRunnable); showMarqueeUI();`(放在 `stopLyricPoll();` 前) |
| 5 | `onPlayStateChanged` STATE_PLAYING 分支 (line 277-285) | 末尾追加:`if (currentMode == Mode.PLAY) { pausedForcesShow = false; if (!marqueeCycleActive) startMarqueeCycle(); else resetMarqueeCycle(); }` |
| 6 | `dispatchKeyEvent` PLAY 分支开头 (line 2250 `if (currentMode == Mode.PLAY) {` 之后) | 插入:`if (event.getAction() == KeyEvent.ACTION_DOWN && isDpadKey(event.getKeyCode()) && !pausedForcesShow && marqueeCycleActive) { resetMarqueeCycle(); }` —— **必须过滤 DPAD 键集合**(LEFT/RIGHT/UP/DOWN/CENTER),BACK/MENU/字母数字键不重置;`isDpadKey` 是新增 static helper(放在 `keyCodeToFocusDirection` 旁);必须在 `mController.handleKeyEvent(event)` 之前,保证 DPAD 任一键都先重置 |
| 7 | `playPrevious` (line 1381) / `playNext` (line 1364) | 不显式加 reset —— `playSong → STATE_PLAYING → onPlayStateChanged` 会自然 reset。**不在切歌路径上加**(避免双源)。 |

### 不改的点

- `controller_karaoke.xml` / `activity_karaoke.xml` / `KaraokeController.java`:不动。
- `togglePlayPause()`:不动,依赖 `onPlayStateChanged(STATE_PAUSED/PLAYING)`。
- `onPause()` / `onResume()`:不动。`onDestroy()` 已有 `mainHandler.removeCallbacksAndMessages(null)`(line 2466),会顺带清掉 cycle Runnable。

### 边界 / 防御

- **音频切歌 (`switchAudioTrack`) 走 `absPlayer.pause()` → `absPlayer.start()`**:会触发 STATE_PAUSED → STATE_PLAYING。期间 `pausedForcesShow` 短暂为 true 然后 false,周期短暂停摆 → reset。可接受(切音轨期间 marquee 强显一会儿没坏处)。
- **`stopPlaybackAndReturnToSelect`** (line 1908) 调 `enterSelectMode()`,后者会 `stopMarqueeCycle()`。✓
- **`remotePausePlay` / `remoteResumePlay`** (line 2580, 2570):走 `mVideoView.pause()` / `start()` → STATE_PAUSED/PLAYING → 自动处理。✓

## Steps

1. **加字段 / 常量**:在 line 164 `marqueeOn` 字段附近加 `CYCLE_SHOW_MS / CYCLE_HIDE_MS / marqueeCycleActive / pausedForcesShow` + 两个匿名内部 Runnable(互引用用 lambda 会被 JLS §8.3.3 拒,匿名内部类的 run() 体是独立方法作用域,无前向引用问题)。
2. **加方法**:`showMarqueeUI` / `hideMarqueeUI` / `startMarqueeCycle` / `stopMarqueeCycle` / `resetMarqueeCycle`(插入到 `updatePlayerQRUI` 后,line 734 附近)。
3. **接线 6 处**(见 Files 表):
   - `enterPlayMode` resume 分支 + fall-through 末尾:`startMarqueeCycle()`
   - `enterSelectMode`:`stopMarqueeCycle()`
   - `onPlayStateChanged` PAUSED/IDLE 分支:暂停强显
   - `onPlayStateChanged` PLAYING 分支末尾:恢复周期(若已 active 则 reset)
   - `dispatchKeyEvent` PLAY 分支开头:ACTION_DOWN 时 resetMarqueeCycle(暂停态跳过)
4. **编译**:`./gradlew :app:assembleArm64GenericNormalDebug` 通过。
5. **Codex review**:至少 1 轮,关注:
   - `cycleHideRunnable` 是否正确 schedule `cycleShowRunnable`(否则 cycle 断)
   - `mainHandler.removeCallbacks` 清干净(避免 Runnable 泄漏)
   - SELECT → PLAY 切换时不会双启动(用 `marqueeCycleActive` 标志位防御)
   - 暂停 → 继续 时 `pausedForcesShow` 标志正确清零(STATE_PLAYING 分支)
   - 切歌(`playSong` → STATE_PLAYING)自然 reset(不需显式 hook `playNext`/`playPrevious`)
   - dispatchKeyEvent 在 `mController.handleKeyEvent` **之前** reset,保证所有 DPAD 键都触发 reset
   - 两个匿名内部 Runnable 互引用是否合法(合法:run() 体是独立方法作用域,字段解析在 run() 触发时;若改用 lambda 会被 JLS §8.3.3 当作简单名前向引用拒绝 —— 已实测)

## Acceptance

PLAY 模式正常播放:

- [ ] 进 PLAY 后 marquee/QR 显示 **8 秒**,然后一起 GONE **7 秒**,循环。
- [ ] 按任何 DPAD 键(上/中/左/右)立即重置:marquee/QR 出现,重新 8s 计时。
- [ ] 长按 seek 期间(每帧 ACTION_DOWN)周期不断被 reset,marquee/QR 保持可见。
- [ ] 切歌(双击左/右、playNext/playPrevious)后,新歌进入 PLAY 时周期正常启动(8s 显优先)。
- [ ] 暂停(DPAD_CENTER 短按):marquee/QR 立即出现并保持,直到继续播放。
- [ ] 继续(再按 DPAD_CENTER):周期恢复(8s 显 → 7s 隐)。

SELECT 模式:

- [ ] 进 SELECT 后 marquee/QR 始终 GONE,没有周期闪现。
- [ ] 从 SELECT 回 PLAY 周期重新启动。

生命周期:

- [ ] `./gradlew :app:assembleArm64GenericNormalDebug` 通过。
- [ ] Codex reviewer 一轮通过(最多 2 轮)。
- [ ] 不破坏 audio-only 修复:`mVideoView` GONE 时 marquee/QR 在 show 相位仍然可见。
- [ ] 不破坏 video MV 模式:video 播放时 marquee/QR 周期正常。

## Notes for the worker

- **复用 `mainHandler`** 作为 cycleHandler,不要再加新字段。
- **`hideMarqueeUI` 只动 view,不动业务态**:`marqueeOn` / `playerQRVisible` 保持原值,这样 show 时 `updateMarqueeUI` / `updatePlayerQRUI` 能正确恢复文本和 bitmap。**严禁**在 hide 时把 `marqueeOn` 改成 false(会破坏业务态)。
- **`pausedForcesShow` 只在 `onPlayStateChanged` 改**,不要在 `dispatchKeyEvent` 里改;按键 reset 时若 `pausedForcesShow == true` 则不 reset(暂停态强显优先)。
- **`enterPlayMode` 多个 return 点都要覆盖**:`start/resume` 早返回分支 + 末尾 fall-through 都要启动周期。
- **不要修改 `controller_karaoke.xml` 或 `activity_karaoke.xml`** —— 视图位置已经对(audio-only 修复时上提过),这次只动 Activity 逻辑。
- **不要修改 `KaraokeController` 的 `show()/hide()`** —— controller 的 show/hide 控制底部条/seek overlay,和顶部 marquee/QR 不再有关(controller 里这些视图已删)。
- **静态自检即可**:本 PLAN 不要求 worker 真机测试,但要在描述里明确告诉 codex_reviewer 静态评审要点(周期边界、暂停态、按键 reset 防御、生命周期清理)。
- **Decision Log**:本 PLAN 基于用户确认的 3 个参数:8s 显 / 7s 隐;DPAD 按键重置;SELECT 维持现状。

## Decision Log

- 2026-06-26: 用户确认周期 8s 显 / 7s 隐(supervisor AskUserQuestion 答案 A)。
- 2026-06-26: 用户确认 DPAD 按键重置周期(AskUserQuestion 答案 A)。
- 2026-06-26: 用户确认 SELECT 模式维持现状(AskUserQuestion 答案 A)。
- 2026-06-26: 暂停态通过 `onPlayStateChanged` 钩子捕获,不依赖 `togglePlayPause()` 显式调用,减少耦合。
