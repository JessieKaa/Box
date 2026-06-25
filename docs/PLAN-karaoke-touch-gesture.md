# PLAN — 卡拉OK PLAY 模式触屏手势 (karaoke-touch-gesture)

## Goal

PLAY 模式下启用触屏手势,行为和 DPAD 遥控器完全对齐:

| 手势 | 等效遥控器键 | 行为 |
|---|---|---|
| 中央单击 | DPAD_CENTER 短按 | 播放/暂停(`togglePlayPause()`) |
| 左半双击 | DPAD_LEFT 双击 | 上一曲(`playPrevious()`) |
| 右半双击 | DPAD_RIGHT 双击 | 下一曲(`playNext()`) |
| 左半长按 | DPAD_LEFT 长按 | 快退(`mController.tvSlideStart(-1)` 脉冲 + `tvSlideStop()` 提交) |
| 右半长按 | DPAD_RIGHT 长按 | 快进(`mController.tvSlideStart(1)` 脉冲 + `tvSlideStop()` 提交) |
| 双指单击 | DPAD_UP 短按 | 切音轨(`switchAudioTrack()`) |

SELECT 模式不启用(透传到子 view)。

## Context

- 现状: PLAY 模式只支持 DPAD 遥控器,触屏点击无任何反应(TYH201H 是触屏 + 遥控双输入)。用户希望触屏体验对齐遥控器。
- 已有方法可复用: `togglePlayPause()` / `playPrevious()` / `playNext()` / `switchAudioTrack()` 都带 Toast 反馈;`mController.tvSlideStart(dir)` / `tvSlideStop()` 已封装 seek 脉冲逻辑(`dir=-1` 倒退 / `dir=1` 快进),并且会和 marquee/QR 周期特性联动(controller.show() 触发)。
- 时间常数: controller 里 `LONG_PRESS_THRESHOLD_MS = 250`,`DOUBLE_TAP_WINDOW_MS = 300`,`SEEK_PULSE_INTERVAL_MS = 100`。触屏手势必须复用这 3 个常数,保证体感和遥控器完全一致。
- marquee/QR 周期特性(merge `b89d5ab`) 已有 `resetMarqueeCycle()`。触屏手势作为"用户交互"应触发 reset,避免手势期间 marquee/QR 突然消失。

## Files

主要文件: `app/src/main/java/com/github/tvbox/osc/karaoke/KaraokeActivity.java`(下文行号基于 worktree HEAD = b89d5ab,实测行号已校准)。

附带小改: `app/src/main/java/com/github/tvbox/osc/karaoke/controller/KaraokeController.java` line 75-77。

### 实测行号(基于 HEAD = b89d5ab)

| 锚点 | 实际行 |
|---|---|
| `KaraokeController` 时间常量 | line 75-77 |
| Activity 字段区(marquee 周期 Runnable 之后) | 插入到 line ~189 之后(`cycleShowRunnable` 定义结束) |
| `dispatchKeyEvent` 开头 | line 2341 |
| `dispatchKeyEvent` 结束 (`return super...`) | line 2391 |
| `enterSelectMode` 开头 | line 836 |
| `onDestroy` 开头 | line 2559 |
| `onDestroy` 末尾 `mainHandler.removeCallbacksAndMessages(null)` | line 2582 |

### 新增 import

`KaraokeActivity.java` 顶部加 `import android.view.MotionEvent;`(目前只有 `KeyEvent` / `View`)。

### KaraokeController.java 改动 (line 75-77)

把 3 个时间常量从 `private static final` 改为 `public static final`:

```java
// 原: private static final long LONG_PRESS_THRESHOLD_MS = 250;
public static final long LONG_PRESS_THRESHOLD_MS = 250;
public static final long DOUBLE_TAP_WINDOW_MS = 300;
public static final long SEEK_PULSE_INTERVAL_MS = 100;
```

注: worker 原本想用 package-private(`static final` 无修饰符),但 controller 在 `com.github.tvbox.osc.karaoke.controller` 子包,activity 在 `com.github.tvbox.osc.karaoke` 父包 —— 不同包,package-private 不生效。编译报"不是公共的; 无法从外部程序包中对其进行访问"。改为 `public static final`。

### 新增字段 (KaraokeActivity.java,插入到 line ~189 之后)

```java
// ===== PLAY 模式触屏手势 =====
// 时间常数复用 KaraokeController(LONG_PRESS_THRESHOLD_MS / DOUBLE_TAP_WINDOW_MS / SEEK_PULSE_INTERVAL_MS),
// 单一事实源 —— 已把 controller 这 3 个常量从 private 改为 package-private。
//
// 状态机:
//   - DOWN: 取消 pending single-tap 回调 + arm long-press 定时器。
//   - 250ms 内 UP: 单击候选 → 记录 lastTouchTapMs + schedule singleTapRunnable。
//   - 250ms 到时: 长按生效,启动 seek 脉冲。
//   - 长按期间 UP: tvSlideStop 提交 seek。
//   - 下一指 DOWN 在 DOUBLE_TAP_WINDOW_MS 内 + 同区: 双击 → playPrevious/playNext + 清 lastTouchTapMs
//     (singleTapRunnable 看到 0 时 no-op)。
//   - 多指(POINTER_DOWN 且 pointerCount>=2): 设 multiPointerTriggered,切音轨;后续 UP 跳过 tap。
private long lastTouchTapMs = 0;          // 上一次 single-tap UP 时间戳(用于双击窗口判定)
private float lastTouchTapX = 0;          // 上一次 single-tap 的 x(用于判定同区双击)
private boolean touchLongArmed = false;   // 长按已触发(避免 UP 时再当 tap)
private int touchLongDir = 0;             // 长按方向: -1=左, 1=右, 0=未长按
private boolean multiPointerTriggered = false;  // 双指切音轨已触发,后续 UP 跳过 tap

// single-tap 延迟回调必须是字段(不能是局部 lambda),这样 DOWN 时能 removeCallbacks 取消
// —— 否则用户按下后 250ms 长按期间,旧 single-tap 回调可能在 300ms 时误触发 togglePlayPause。
private final Runnable touchSingleTapRunnable = new Runnable() {
    @Override
    public void run() {
        if (lastTouchTapMs > 0) {
            lastTouchTapMs = 0;
            togglePlayPause();
            mController.show();
            resetMarqueeCycle();
        }
    }
};
private final Runnable touchLongPulseRunnable = new Runnable() {
    @Override
    public void run() {
        if (touchLongArmed && touchLongDir != 0) {
            mController.tvSlideStart(touchLongDir);
            mainHandler.postDelayed(this, KaraokeController.SEEK_PULSE_INTERVAL_MS);
        }
    }
};
private final Runnable touchLongArmRunnable = new Runnable() {
    @Override
    public void run() {
        // 250ms 内没 UP → 长按生效
        touchLongArmed = true;
        mController.tvSlideStart(touchLongDir);
        mainHandler.postDelayed(touchLongPulseRunnable, KaraokeController.SEEK_PULSE_INTERVAL_MS);
    }
};
```

注: lambda 形式的 Runnable 在 JLS §8.3.3 看作"简单名前向引用",在字段初始化器里被禁。改用匿名内部类,run() 体在调用时才解析字段,所有字段已就绪。复用 controller 常量避免双处魔法数字。

### 新增方法 (插入到 `dispatchKeyEvent` 后,line 2392 之后)

```java
// ======================== Touch Handling (PLAY mode) ========================

@Override
public boolean dispatchTouchEvent(MotionEvent ev) {
    if (currentMode == Mode.PLAY && handlePlayModeTouch(ev)) {
        return true;  // 消费,不下发
    }
    return super.dispatchTouchEvent(ev);
}

/**
 * PLAY 模式触屏手势分发。返回 true 表示消费(不下发到子 view)。
 * SELECT 模式直接走 super,所有触屏交互透传到子 view。
 *
 * 手势 → 行为映射(对齐 DPAD 遥控器):
 *   - 中央单击     → togglePlayPause (= DPAD_CENTER)
 *   - 左半双击     → playPrevious    (= DPAD_LEFT 双击)
 *   - 右半双击     → playNext        (= DPAD_RIGHT 双击)
 *   - 左半长按     → tvSlideStart(-1) 脉冲 + tvSlideStop  (= DPAD_LEFT 长按)
 *   - 右半长按     → tvSlideStart(+1) 脉冲 + tvSlideStop  (= DPAD_RIGHT 长按)
 *   - 双指单击     → switchAudioTrack (= DPAD_UP)
 */
private boolean handlePlayModeTouch(MotionEvent ev) {
    int action = ev.getActionMasked();
    int pointerCount = ev.getPointerCount();

    // 双指单击 → 切音轨(优先级最高,且吞掉后续 UP 的 tap 触发)
    if (pointerCount >= 2 && action == MotionEvent.ACTION_POINTER_DOWN) {
        cancelTouchGestureState();          // 取消 pending long-press / single-tap
        multiPointerTriggered = true;       // 后续 UP 不能再触发 single-tap
        switchAudioTrack();
        mController.show();
        resetMarqueeCycle();
        return true;
    }

    // 多指抢占后,任何后续 UP/POINTER_UP 都只清 flag,不走 tap/long-press 分支
    if (multiPointerTriggered) {
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            multiPointerTriggered = false;
        }
        return true;  // 全程消费,避免子 view 收到 inconsistent sequence
    }

    float x = ev.getX();
    int width = getWindow().getDecorView().getWidth();
    boolean isLeft = x < width / 2f;
    boolean isRight = !isLeft;

    switch (action) {
        case MotionEvent.ACTION_DOWN:
            // 关键: 取消 pending single-tap 回调。否则用户按下后 250ms 长按期间,
            // 上一指的 togglePlayPause 回调(300ms 时)可能误触发。
            mainHandler.removeCallbacks(touchSingleTapRunnable);
            touchLongDir = isLeft ? -1 : 1;  // 预设方向
            mainHandler.removeCallbacks(touchLongArmRunnable);
            mainHandler.postDelayed(touchLongArmRunnable, KaraokeController.LONG_PRESS_THRESHOLD_MS);
            return true;  // 消费 DOWN,保证后续 MOVE/UP 进来

        case MotionEvent.ACTION_MOVE:
            // 不做 touchSlop 检测;长按期间允许微抖动
            return true;

        case MotionEvent.ACTION_POINTER_UP:
            // 不应该走到这里(multiPointerTriggered 已拦截),防御性消费
            return true;

        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL: {
            mainHandler.removeCallbacks(touchLongArmRunnable);
            if (touchLongArmed) {
                // 长按刚结束 → 提交 seek
                mainHandler.removeCallbacks(touchLongPulseRunnable);
                mController.tvSlideStop();
                touchLongArmed = false;
                touchLongDir = 0;
                mController.show();
                resetMarqueeCycle();
                return true;
            }
            // 否则视为 tap(单击或双击的一指)
            long now = System.currentTimeMillis();
            boolean sameZoneAsLast = (lastTouchTapMs > 0)
                    && ((isLeft && lastTouchTapX < width / 2f)
                        || (isRight && lastTouchTapX >= width / 2f));
            if (sameZoneAsLast
                    && now - lastTouchTapMs <= KaraokeController.DOUBLE_TAP_WINDOW_MS) {
                // 双击
                if (isLeft) playPrevious();
                else playNext();
                lastTouchTapMs = 0;  // 重置,避免三击算两次双击
                // singleTapRunnable 在 DOWN 时已 removeCallbacks;双击分支无需再撤
                mController.show();
                resetMarqueeCycle();
            } else {
                // 单击(候选) — 记录,等下一次 DOWN 判定是否升级为双击
                lastTouchTapMs = now;
                lastTouchTapX = x;
                mainHandler.removeCallbacks(touchSingleTapRunnable);
                mainHandler.postDelayed(touchSingleTapRunnable,
                        KaraokeController.DOUBLE_TAP_WINDOW_MS);
            }
            touchLongDir = 0;
            return true;
        }
    }
    return false;
}

/**
 * 取消所有 touch gesture pending 状态。用于:
 *   - 多指抢占时清掉 pending long-press / single-tap
 *   - enterSelectMode 切换时(避免悬挂的长按定时器在 SELECT 模式触发)
 *   - onDestroy 清理(防御性,onDestroy 已 removeCallbacksAndMessages)
 */
private void cancelTouchGestureState() {
    mainHandler.removeCallbacks(touchSingleTapRunnable);
    mainHandler.removeCallbacks(touchLongArmRunnable);
    mainHandler.removeCallbacks(touchLongPulseRunnable);
    if (touchLongArmed) {
        mController.tvSlideStop();
    }
    touchLongArmed = false;
    touchLongDir = 0;
    lastTouchTapMs = 0;
    multiPointerTriggered = false;
}
```

### 修改点

| # | 文件 / 方法 (line) | 改动 |
|---|---|---|
| 1 | `KaraokeController.java` line 75-77 | 3 个常量从 `private static final` 改为 `public static final`(原 PLAN 写 package-private,实施时发现 controller 在子包,必须 public 才能跨包访问) |
| 2 | `KaraokeActivity.java` 顶部 import | 加 `import android.view.MotionEvent;` |
| 3 | `KaraokeActivity.java` 字段区 (line ~189 之后) | 加 touch gesture 字段 + 3 个 Runnable(匿名内部类形式,非 lambda) |
| 4 | `KaraokeActivity.java` `dispatchKeyEvent` 后 (line ~2392 之后) | 加 `dispatchTouchEvent` / `handlePlayModeTouch` / `cancelTouchGestureState` |
| 5 | `KaraokeActivity.java` `enterSelectMode` 末尾 (line ~901) | 追加 `cancelTouchGestureState();` 防止 PLAY→SELECT 时长按定时器悬挂 |
| 6 | `KaraokeActivity.java` `onDestroy` (line ~2582) | 已有 `mainHandler.removeCallbacksAndMessages(null)`,显式调用是冗余但防御 —— 决定不加,避免 noise |

### 不改的点

- `controller_karaoke.xml` / `activity_karaoke.xml`: 不动。
- `KaraokeController.handleKeyEvent()`: 不动,DPAD 路径不受影响。
- `mController.tvSlideStart/Stop`: 不动,直接复用。
- `togglePlayPause` / `playPrevious` / `playNext` / `switchAudioTrack`: 不动,直接复用。
- marquee/QR 周期逻辑: 不动,但每次手势都调 `resetMarqueeCycle()`(已在 `handlePlayModeTouch` 里)。

## Steps

1. **改 controller 常量可见性**: `KaraokeController` line 75-77 的 3 个时间常量去 `private`,改为 package-private `static final`。值/类型不变。
2. **加 import**: `KaraokeActivity` 顶部加 `import android.view.MotionEvent;`。
3. **加字段**: 在 line ~189 之后(`cycleShowRunnable` 定义结束后)插入 touch gesture 字段块。包括:
   - `lastTouchTapMs / lastTouchTapX / touchLongArmed / touchLongDir / multiPointerTriggered`
   - 3 个 `final Runnable`: `touchSingleTapRunnable` / `touchLongPulseRunnable` / `touchLongArmRunnable`
   - 都用匿名内部类形式(不用 lambda,避免字段初始化器前向引用问题)
4. **加方法**: 在 `dispatchKeyEvent` 返回后(line ~2392)插入 `dispatchTouchEvent` / `handlePlayModeTouch` / `cancelTouchGestureState`。
5. **接线**:
   - `enterSelectMode` 末尾(line ~901)加 `cancelTouchGestureState();`
   - `onDestroy` 已有 `mainHandler.removeCallbacksAndMessages(null)`(line 2582),无需额外 remove
6. **编译**: `./gradlew :app:assembleArm64GenericNormalDebug` 通过。
7. **Codex review**: 至少 1 轮(最多 2 轮)。重点检查:
   - **`multiPointerTriggered` flag 是否正确覆盖所有 multi-pointer UP 路径** — 包括 `ACTION_POINTER_UP` 显式 case
   - **DOWN 是否取消 `touchSingleTapRunnable`** — 防止用户按下新指时上一指的 togglePlayPause 回调误触发
   - **双击分支是否调 `mController.show()` + `resetMarqueeCycle()`** — 对齐 DPAD 体验
   - **`cancelTouchGestureState` 是否清所有 flag + removeCallbacks** — 包括 `multiPointerTriggered`
   - `dispatchTouchEvent` 在 SELECT 模式透传(返回 super)是否正确
   - 长按期间 `touchLongPulseRunnable` 自我 postDelayed 是否会泄漏(UP 时 removeCallbacks)
   - 双击窗口判定: `lastTouchTapMs = 0` 是否正确防止三击算两次双击
   - audio-only 模式 `mVideoView` GONE 时,`dispatchTouchEvent` 是否仍能收到 event(activity 级 dispatch 不依赖具体子 view 可见性,应该能收到)

## Acceptance

PLAY 模式:

- [ ] 单击屏幕中央(任意位置实际都行,但建议中央):播放/暂停切换 + Toast。
- [ ] 双击左半屏:上一曲 + Toast。
- [ ] 双击右半屏:下一曲 + Toast。
- [ ] 长按左半屏:出现 seek overlay(快退图标 + 时间),松手后 seek 提交。
- [ ] 长按右半屏:出现 seek overlay(快进图标 + 时间),松手后 seek 提交。
- [ ] 双指单击屏幕:切音轨 + Toast(若歌有多音轨)/ 提示无多音轨。
- [ ] 任一手势触发后,marquee/QR 周期被 reset(立即显示 8s)。
- [ ] 暂停态(DPAD_CENTER 或单击触发)下,长按/双击仍能切歌、切音轨(只 seek 在暂停态下没实际效果,但 tvSlideStart 内部会处理)。

SELECT 模式:

- [ ] 触屏正常上下滑、点歌曲、点 tab,手势不拦截。
- [ ] PLAY → SELECT → PLAY 切换后,手势状态干净(无悬挂的 long-press timer)。

生命周期:

- [ ] `./gradlew :app:assembleArm64GenericNormalDebug` 通过。
- [ ] Codex reviewer 一轮通过(最多 2 轮)。
- [ ] 不破坏 marquee/QR 周期特性。
- [ ] 不破坏 audio-only 修复。
- [ ] 不破坏 DPAD 遥控器路径(controller.handleKeyEvent 不变)。

## Notes for the worker

- **不要**在 `mVideoView` 或 `bgCarouselView` 上加 OnTouchListener —— 用 activity 级 `dispatchTouchEvent` 拦截,避免 audio-only 时 mVideoView GONE 导致 listener 失效。
- **controller 常量改可见性**: 这是对 "仅改 KaraokeActivity" 约束的必要放松,改动量仅 3 行(`private` → 空)。如果不想改 controller,可在 activity 里复制一份常量,但会增加双处魔法数字的同步风险。**本 PLAN 选择改 controller 可见性**,codex review 时说明理由。
- **双指切音轨的判定**: 必须用 `ACTION_POINTER_DOWN` + `pointerCount >= 2`,不能用 `ACTION_DOWN`(单指 down 永远是 1 指)。第二指落下时触发。
- **双指后的 UP 副作用补丁**(原 PLAN 标注的 worker 必加项): 已实装 `multiPointerTriggered` flag —— 在 `ACTION_POINTER_DOWN` 双指分支设 true,在后续 `ACTION_UP`/`ACTION_CANCEL` 时清零并 return true(跳过 tap/long-press 分支)。`cancelTouchGestureState()` 也清零。
- **`touchSingleTapRunnable` 必须是字段而非局部 lambda** —— DOWN 时要 `removeCallbacks` 取消上一指的延迟回调,否则用户按下第二指时第一指的 togglePlayPause(300ms 后)可能在长按期间(250ms 后)误触发。**这是原 PLAN 没明说的隐含补丁,实施时必须按本 PLAN 字段定义实装**。
- **lambda 改匿名内部类**: 字段初始化器里的 lambda 会被 JLS §8.3.3 当作"简单名前向引用"拒绝(`touchLongArmRunnable` 引用 `touchLongPulseRunnable` 时)。改用匿名内部类,run() 体在调用时才解析字段,所有字段已就绪。参考已有的 `cycleHideRunnable` / `cycleShowRunnable` 模式(line 176-189)。
- **复用 controller 的 seek 脉冲**: `mController.tvSlideStart(dir)` 已封装"加速 + 显示 overlay + show()"逻辑,不要在 activity 里重写。长按分支无需额外调 `mController.show()`(tvSlideStart 内部已经调),但单击/双击/多指分支需要显式调。
- **暂停态下 tvSlideStart 的行为**: 暂停时 `mVideoView.isPlaying() = false`,`wasPlayingBeforeSeek = false`,seek 提交后不会自动 start。这是 controller 的现有行为,符合预期(暂停态 seek 后保持暂停)。
- **不要在 SELECT 模式启用**: `dispatchTouchEvent` 第一行 `if (currentMode == Mode.PLAY && handlePlayModeTouch(ev))` 已保证。SELECT 模式直接 `super.dispatchTouchEvent`,RV/EditText 等子 view 正常收 touch event。
- **`mController.show()` 策略**: 每个手势分支末尾都调一次 `mController.show()`(对齐 DPAD 路径 line 2358-2360 的模式)。长按分支靠 `tvSlideStart` 内部的 `updateSeekOverlay → show()` 自动 show,不重复调。

## Decision Log

- 2026-06-26: 用户确认"左中右三区 + 双指切音轨"(AskUserQuestion 答案 A)。
- 2026-06-26: 用户确认"不做滑动手势"(AskUserQuestion 答案 A)。
- 2026-06-26: 用户确认"仅 PLAY 启用"(AskUserQuestion 答案 A)。
- 2026-06-26: controller 时间常量改可见性而非复制,worker 实施时如果 review 反对再调整。
- 2026-06-26: 单击触发播放/暂停 — 与 DPAD_CENTER 短按完全对齐。原方案考虑过"单击只显示 controller,再单击才暂停",但与 DPAD 行为不一致,放弃。

## Refinements (worker 细化,2026-06-26)

实测对比 worktree HEAD = `b89d5ab` 后,对原 PLAN 的细化:

1. **行号校准**: `enterSelectMode` 实际 836(原 PLAN 写 ~748);`dispatchKeyEvent` 实际 2341-2392(原 PLAN 写 ~2400);`onDestroy` 实际 2559(原 PLAN 写 ~2466);`onDestroy` 末尾已有 `mainHandler.removeCallbacksAndMessages(null)` line 2582,无需追加。
2. **`multiPointerTriggered` flag 显式化**: 原 PLAN Notes 提到 "worker 实施时必须加上",现已写入字段定义 + handlePlayModeTouch 分支 + cancelTouchGestureState,作为算法的一部分而非补丁。
3. **`touchSingleTapRunnable` 升级为字段**: 原 PLAN 用局部 lambda postDelayed,无法在 DOWN 时取消 —— 会导致用户按下新指(250ms 长按期间)时,上一指的 togglePlayPause 回调(300ms 后)误触发。改用字段 + DOWN 时 removeCallbacks。
4. **`mController.show()` 加到每个手势分支**: 对齐 DPAD dispatchKeyEvent 的 `mController.show()` 调用(line 2359),保证用户操作后 bottom bar 显示 5s(可见 play/pause 按钮态)。
5. **`resetMarqueeCycle()` 补到双击分支**: 原 PLAN 漏了。所有手势都应重置周期,双击不能例外。
6. **lambda 改匿名内部类**: 字段初始化器里的 lambda(`touchLongArmRunnable` 引用 `touchLongPulseRunnable`)会被 JLS §8.3.3 拒绝。改用匿名内部类。参考已有 `cycleHideRunnable` 模式。
7. **`ACTION_POINTER_UP` 显式 case**: 即使 `multiPointerTriggered` 拦截了正常路径,加一个显式 case 防御性消费,避免落到 `return false` 然后 super.dispatchTouchEvent 收到不一致的 sequence。

## Pending Issues

- **codex reviewer 未响应 (2026-06-26)**: Phase 1 阶段 1 评审尝试 `assign` + `handoff` 均未取得实质性反馈 —— `assign` 后 terminal 立即变 completed 但无 send_message 回报;`handoff` 仅返回 spinner 状态而非评审内容。怀疑 codex_reviewer profile 未实际跑 codex CLI 或环境异常。worker 已对照 PLAN 自查算法正确性(见 Refinements #2/#3/#6 列出的 race / lambda 前向引用 / 多指 UP 副作用,均已实装补丁),进入阶段 2 实施。如果 Phase 2 codex review 仍无响应,按 Step 9.5 上报 [BLOCKED]。

