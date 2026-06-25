# PLAN — 卡拉OK TV遥控器操作逻辑 (karaoke-remote-controls)

## Goal

重写卡拉OK **PLAY 模式**下的 TV 遥控器按键行为：上=切音轨、中=播放/暂停、左/右长按=快退/快进、左/右双击=前/后一曲，且**每次操作都要有 Toast 提示**。

## Context

- 现状（`KaraokeController.handleKeyEvent`）：
  - DPAD_CENTER/SPACE → `onTogglePlayPause()` ✓ 维持
  - DPAD_UP → `onPrevious()`（要改成切音轨）
  - DPAD_DOWN → `onNext()`（用户未提；保留为可选辅助，但**默认不再触发** onNext——避免和"双击右=下一首"语义冲突；若想保留可在 plan review 时讨论）
  - DPAD_LEFT/RIGHT → `tvSlideStart(dir)` / `tvSlideStop()`（按下进入 simSlide 模式，松开提交 seek；目前没有"双击=切歌"逻辑）
  - MENU/INFO → `onBackToSelect()` ✓ 维持
  - A 键 → `onSwitchAudioTrack()`（仍可保留作为备用，但**主要改用 DPAD_UP**）
- 用户要求的新映射：
  | 按键 | 行为 | Toast |
  |---|---|---|
  | DPAD_UP（点按） | 切音轨（原唱/伴奏切换） | `已切换至：原唱` 或 `已切换至：伴奏` |
  | DPAD_CENTER（点按） | 播放/暂停 | `播放中` / `已暂停` |
  | DPAD_LEFT **长按** | 持续快退（按住期间持续 -1.5s/100ms） | 进度浮层即可（沿用 `llSeekOverlay`） |
  | DPAD_RIGHT **长按** | 持续快进 | 同上 |
  | DPAD_LEFT **双击** | 前一曲 | `上一曲：{title}` |
  | DPAD_RIGHT **双击** | 后一曲 | `下一曲：{title}` |
- 所有 Toast 必须用现有 `Toast.makeText(mContext, ..., Toast.LENGTH_SHORT).show()`，不要拉新依赖。
- 长按/双击的事件判定要在 ACTION_DOWN/ACTION_UP/MULTIPLE 之间分清；不能用 `onKeyLongPress`（项目未启用），需要 dispatcher 自己用 Handler 计时。
- 卡拉OK 的"原唱/伴奏"切换通过 `TrackInfo#getAudio()` 在多音轨歌曲里切换；歌曲只有单一音轨时，按钮要 Toast `当前歌曲无多音轨` 而非沉默。
- 现有 `tvSlideStart/tvSlideStop` 仍是可复用的 seek 框架（有 `llSeekOverlay` 进度浮层 UI），但需要把「按下立即进 simSlide」改成「按下后 250ms 仍没松手才认为是长按 seek」——这样双击时不会误触 seek。

## Files

预测改动文件：

- `app/src/main/java/com/github/tvbox/osc/karaoke/controller/KaraokeController.java`
  - 重写 `handleKeyEvent(KeyEvent)`：
    - 加字段 `long lastLeftTapMs=0, lastRightTapMs=0;` `Handler tapHandler;` `Runnable leftLongSeekRunnable, rightLongSeekRunnable;` `boolean leftLongArmed, rightLongArmed;`
    - DPAD_LEFT / RIGHT ACTION_DOWN：
      1. 距上次同方向 ACTION_UP ≤ 300ms → **双击**：取消长按定时器，调用 `callback.onPrevious()` / `onNext()`，发 Toast，标记本次为"已消费双击"。
      2. 否则：起一个 250ms 的 `postDelayed` 长按定时器；期间若 ACTION_UP 先到 → 当作单击（消费，不发 seek），并记录本次 tap 时间用于下一次双击判定。
      3. 长按定时器触发：标记 `leftLongArmed=true`，调用现有 `tvSlideStart(-1)` / `tvSlideStart(1)`（持续 seek）。
    - DPAD_LEFT / RIGHT ACTION_UP：若 `leftLongArmed` → `tvSlideStop()` + 清 armed；否则什么也不做（单击已被定时器判定）。
    - DPAD_UP ACTION_DOWN：调用 `callback.onSwitchAudioTrack()`。
    - DPAD_CENTER ACTION_DOWN：维持 `callback.onTogglePlayPause()`。
    - DPAD_DOWN ACTION_DOWN：**默认改成"什么都不做"返回 true 吞掉**（避免误切下一首，与双击右冲突）。如需保留旧义，留 TODO 注释。
- `app/src/main/java/com/github/tvbox/osc/karaoke/KaraokeActivity.java`
  - `KaraokeControllerCallback` 实现处（已有 `onSwitchAudioTrack`/`onTogglePlayPause`/`onPrevious`/`onNext`）增加 Toast：
    - `onSwitchAudioTrack()` 切完音轨后，读 `mVideoView.getTrackInfo().getAudio()` 当前选中音轨名，Toast `已切换至：{name}`；无多音轨则 Toast `当前歌曲无多音轨`。
    - `onTogglePlayPause()` 按当前 `mVideoView.isPlaying()` Toast `播放中`/`已暂停`。
    - `onPrevious()`/`onNext()` 取即将播放歌曲的 title，Toast `上一曲：{title}` / `下一曲：{title}`；无更多曲时沿用 `R.string.karaoke_no_more`。
- `app/src/main/res/values/strings.xml`：新增
  - `karaoke_toast_playing`="播放中"
  - `karaoke_toast_paused`="已暂停"
  - `karaoke_toast_track_switched`="已切换至：%1$s"
  - `karaoke_toast_no_multi_track`="当前歌曲无多音轨"
  - `karaoke_toast_prev_song`="上一曲：%1$s"
  - `karaoke_toast_next_song`="下一曲：%1$s"
  - `karaoke_toast_no_more_songs`="已到最后一首"

## Steps

1. **抽双击/长按工具**：在 `KaraokeController` 顶部加一个小型 `DpadGestureDetector`（或直接内联），实现 250ms 长按门槛 + 300ms 双击窗口；在 ACTION_UP 时判定单击/双击/长按结束。
2. **重写 LEFT/RIGHT 分支**：用上面工具替换原"`tvSlideStart` 立即调用"的写法，但 seek 实际仍走 `tvSlideStart/Stop`。**确保 simSlideOverlay UI 正常**——长按期间显示 `llSeekOverlay`，松开时 `tvSlideStop` 提交并隐藏 overlay（已有逻辑）。
3. **改 UP 分支为切音轨**：调用现有 `callback.onSwitchAudioTrack()`（保留 A 键作为备用）。
4. **Activity 侧 Toast**：按 Files 节列出的字符串补 Toast；切音轨后读取实际音轨名而非写死"原唱/伴奏"（远端 MV 多音轨时名可能为 "Original/Accompaniment"，按实际 name 显示更友好）。
5. **strings.xml**：补 7 条文案（同时改 values-zh-rCN 若存在）。
6. **构建+手测**：`./gradlew assembleArm64GenericNormalDebug`；上 TV 设备：
   - 中键：能播放/暂停，每次都有 Toast。
   - 上键：能切原唱/伴奏，Toast 显示新音轨名。
   - 长按左/右：进度浮层出现且按住期间持续 seek，松手落地。
   - 双击左/右：切到前/后一曲，Toast 显示新曲标题；**不能**误触发 seek。
   - 双击但松手 < 250ms：单击不触发 seek，下一首/前一曲正常触发。

## Acceptance

PLAY 模式下：

- [ ] 按中键能播放/暂停，Toast 显示对应文案。
- [ ] 按上键能切原唱/伴奏；多音轨歌曲显示 `已切换至：{音轨名}`；单音轨歌曲显示 `当前歌曲无多音轨`。
- [ ] 长按左/右键能持续 seek，松手后落地；进度浮层（`llSeekOverlay`）显示正确。
- [ ] 双击左/右键切前/后一曲，Toast 显示曲名；双击过程不触发 seek。
- [ ] 单纯单击左/右（没有第二次 tap）**不切歌、不 seek**（视为"等待双击窗口超时"）。
- [ ] 所有 Toast 用 `Toast.LENGTH_SHORT`，不引入新依赖。
- [ ] `./gradlew assembleArm64GenericNormalDebug` 通过。
- [ ] Codex reviewer 一轮通过（最多 2 轮）。

## Notes for the worker

- **DPAD_DOWN 默认吞掉** 是产品决策，避免"按双击右下一首"与"按下下一首"双触发；若 codex 评审反对，可以改成"DPAD_DOWN 也进队列下一首但不再 Toast"——交由评审讨论。
- 长按阈值 250ms、双击窗口 300ms 是建议值，与 Android ViewConfiguration 的 `getLongPressTimeout()`（默认 400ms）和 `getDoubleTapTimeout()`（默认 300ms）量级一致；可读 `ViewConfiguration.get(ctx).getDoubleTapTimeout()` 替代硬编码。
- 双击窗口内若用户第三次按下（三连击），按"双击 + 新一轮单击"处理即可，不必实现三击。
- 切音轨后读音轨名：`mVideoView.getTrackInfo().getAudio()` 列表里找 `selected==true` 那条；TrackInfoBean.name 来自 IJK/Exo 解析，多音轨伴奏轨一般 name 含 "伴奏"/"Accompaniment"——直接显示 name 即可。
- 若 `mVideoView` 在切歌中状态不稳，Toast 文本可降级到"已切换音轨"，但**不能崩**。
- **`onSwitchAudioTrack()` 的最终落地**：统一改为"直接切下一条音轨 + Toast"，不再弹 `SelectDialog`。audioTracks.size()<=1 → Toast `当前歌曲无多音轨`；size>=2 时按 `(selectedIdx+1) % size` 轮转，Toast `已切换至:{新音轨 name}`。所有调用点（DPAD_UP、A 键、ivAudioTrack 按钮）共享同一行为。详见 Decision Log。

## Decision Log

- 2026-06-25 — DPAD_UP 切音轨：选项 A（统一直接切 + Toast），不弹 dialog。Decision by supervisor after worker [BLOCKED]: PLAN Files 节对 `onSwitchAudioTrack()` 的描述被确认为「直接切下一条 + Toast」，所有调用点共享行为；触屏用户失去 dialog 选项被接受（卡拉OK 是 TV 场景，多音轨 MV 通常 2 条，直接切够用）。
