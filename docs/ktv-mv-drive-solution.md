# KTV 点歌播放器改造方案

## 1. 背景

当前项目已经具备以下基础能力：

- 本地目录浏览
- WebDAV 目录浏览
- 外接存储根目录发现
- 目录内视频文件筛选与单文件播放
- 本地目录索引与检索雏形

现有相关模块主要包括：

- `app/src/main/java/com/github/tvbox/osc/ui/activity/DriveActivity.java`
- `app/src/main/java/com/github/tvbox/osc/viewmodel/drive/LocalDriveViewModel.java`
- `app/src/main/java/com/github/tvbox/osc/viewmodel/drive/WebDAVDriveViewModel.java`
- `app/src/main/java/com/github/tvbox/osc/util/DrivePlayHelper.java`
- `app/src/main/java/com/github/tvbox/osc/util/StorageRootFinder.java`
- `app/src/main/java/com/github/tvbox/osc/data/HomeFolderIndexManager.java`
- `app/src/main/java/com/github/tvbox/osc/ui/activity/FolderIndexSearchActivity.java`
- `app/src/main/java/com/github/tvbox/osc/ui/activity/PlayActivity.java`

这说明项目距离“可用的 KTV MV 点歌播放器”并不远，但目前仍停留在“文件浏览器 + 单视频播放”的阶段，还没有建立完整的“歌库 + 点歌 + 排队 + 连播”能力。

本文档给出一份落地方案，目标是在尽量复用现有代码的前提下，使程序支持读取 `WebDAV / 本地磁盘 / 外接磁盘` 中的 MV 文件，并提供类似 KTV 的点歌体验。

## 2. 目标

### 2.1 目标能力

实现一个面向电视遥控器场景的本地/网络 MV 点歌播放器，支持：

- 挂载和管理多个媒体源
- 浏览本地磁盘、外接磁盘、WebDAV 歌库
- 对歌库建立索引
- 按歌曲名、歌手、拼音、首字母检索
- 将歌曲加入点歌队列
- 支持下一首、上一首、插播、删除待播
- 支持顺序播放、单曲循环、列表循环
- 展示已点列表和当前播放状态
- 对网络源失败、重试、权限异常进行容错处理

### 2.2 非目标

首个版本不建议直接纳入以下能力：

- 实时麦克风采集与打分
- 音轨分离和人声消除
- 在线曲库抓取
- 云端账号同步
- 多终端并发抢歌

这些能力会显著放大工程复杂度，不适合作为第一阶段目标。

## 3. 当前状态评估

## 3.1 已有能力

### 存储接入

- `DriveActivity` 已支持添加 `LOCAL / WEBDAV / ALISTWEB` 三类盘源。
- `StorageRootFinder` 已能发现内部存储和部分外接存储路径。
- `WebDAVDriveViewModel` 已通过 Sardine 执行目录列举。

### 文件筛选

- `StorageDriveType.isVideoType()` 已有较大的视频扩展名白名单。
- 本地和 WebDAV 浏览中都已按扩展名判断是否为视频。

### 播放链路

- `DrivePlayHelper` 能把文件路径或 URL 包装成 `VodInfo` 并跳转到 `PlayActivity`。
- `PlayActivity` 已有完整视频播放能力，适合作为 KTV 播放页的底座。

### 本地索引雏形

- `HomeFolderIndexManager` 已支持本地目录递归扫描视频文件并写入数据库。
- `FolderIndexSearchActivity` 已支持基于索引关键词检索并直接播放。

## 3.2 当前缺口

### 数据模型缺口

当前系统把 MV 当作普通视频文件处理，缺少 KTV 语义层：

- 没有“歌曲”实体
- 没有“歌手”字段
- 没有“拼音/首字母”字段
- 没有“点歌队列”实体
- 没有“当前播放上下文”实体

### 交互缺口

当前仅支持：

- 进目录
- 选中文件
- 直接播放

缺少：

- 点歌而不是立即播放
- 已点列表
- 插播/置顶
- 自动播放下一首
- 遥控器友好的点歌入口
- KTV 风格歌曲筛选入口

### 索引范围缺口

当前索引只覆盖本地文件夹快捷方式，不覆盖：

- WebDAV 歌库
- 多媒体源统一索引
- 元数据解析

### 权限与稳定性缺口

- Android 11+ 外接存储访问依赖高权限，兼容性存在设备差异。
- WebDAV 列表与播放可能受认证、超时、编码、目录层级影响。
- 播放器虽然能播单文件，但缺少“队列驱动”的播放调度。

## 4. 总体方案

建议采用“三层改造”：

1. 存储源统一接入层
2. 歌库索引与检索层
3. KTV 点歌与播放控制层

## 4.1 存储源统一接入层

目标是把本地盘、外接盘、WebDAV 统一抽象为“媒体源”。

建议新增统一接口，例如：

```java
public interface MediaLibraryProvider {
    String getSourceId();
    String getDisplayName();
    List<MediaEntry> list(String path) throws Exception;
    InputStream open(String path) throws Exception; // 可选
    String resolvePlayableUrl(String path) throws Exception;
    boolean supportsIndexing();
}
```

建议实现：

- `LocalMediaLibraryProvider`
- `WebDavMediaLibraryProvider`
- `AlistMediaLibraryProvider`，后续可继续沿用

说明：

- 外接磁盘本质上仍属于 `LocalMediaLibraryProvider`，只是根路径不同。
- `resolvePlayableUrl()` 用于把媒体条目转成播放器可直接消费的路径或 URL。
- 本层不处理 KTV 队列，只处理“列目录、拿文件、生成播放地址”。

## 4.2 歌库索引与检索层

这是把“文件系统”升级成“歌库”的关键层。

建议新增数据实体：

### `KtvMediaSource`

字段建议：

- `id`
- `type`
- `displayName`
- `rootPathOrUrl`
- `configJson`
- `enabled`
- `lastScanAt`
- `scanStatus`
- `scanError`

### `KtvSong`

字段建议：

- `id`
- `sourceId`
- `sourceType`
- `filePath`
- `playUrl`
- `fileName`
- `title`
- `artist`
- `album`
- `category`
- `pinyin`
- `initials`
- `duration`
- `fileSize`
- `lastModified`
- `coverPath`
- `lyricPath`
- `isFavorite`

### `KtvPlayQueueItem`

字段建议：

- `id`
- `songId`
- `songTitle`
- `artist`
- `playUrl`
- `status`
- `queueOrder`
- `createdAt`

### 元数据来源

第一阶段不建议依赖复杂的媒体标签解析，优先用文件名规则提取：

- `歌手 - 歌名.mp4`
- `歌名 - 歌手.mkv`
- `歌手_歌名.avi`

可增加一个文件名解析器：

- `KtvSongNameParser`

负责把文件名拆解为：

- `title`
- `artist`
- `initials`
- `pinyin`

后续再补：

- NFO/同名 JSON 元数据
- 内嵌媒体标签
- 同名封面与歌词文件

## 4.3 KTV 点歌与播放控制层

这是和普通播放器最不同的部分。

建议增加以下核心能力：

### 歌曲浏览方式

- 按媒体源浏览
- 按歌手浏览
- 按首字母浏览
- 按最近更新浏览
- 按热门/收藏浏览

### 点歌队列能力

- 点歌加入队尾
- 插播到下一首
- 删除待播歌曲
- 清空队列
- 当前播放结束自动播放下一首

### 播放模式

- 顺序播放
- 单曲循环
- 列表循环

### KTV 遥控器操作

建议遥控器键位约定：

- `OK`：立即播放或加入队列
- `菜单键`：弹出操作菜单
- `左/右`：切换分类页签
- `上/下`：列表导航
- `返回`：返回上一层或退出点歌弹层

## 5. 建议的界面方案

## 5.1 一级入口

建议在首页增加一个独立入口：

- `KTV 点歌`

不要把它完全塞进现有 `DriveActivity`。

原因：

- `DriveActivity` 更像“文件浏览器”
- `KTV 点歌` 更像“歌库应用”
- 二者交互模型不同

## 5.2 页面结构建议

### 页面 A：KTV 首页

入口模块：

- 媒体源
- 歌手点歌
- 拼音点歌
- 最近添加
- 收藏歌曲
- 已点列表

### 页面 B：歌库列表页

支持：

- 搜索框
- 分类切换
- 当前来源过滤
- “加入已点”
- “立即播放”

### 页面 C：已点队列页

展示：

- 当前播放
- 下一首
- 后续队列

支持：

- 删除
- 上移
- 插播
- 清空

### 页面 D：播放页

基于现有 `PlayActivity` 改造，新增：

- 当前歌曲名/歌手展示
- 队列侧边栏
- 播放完成后自动切下一首
- 简化 KTV 模式下无关按钮

## 6. 代码改造建议

## 6.1 复用现有模块

建议保留并复用：

- `DrivePlayHelper`
- `PlayActivity`
- `StorageRootFinder`
- `WebDAVDriveViewModel` 中的认证与列目录逻辑
- `HomeFolderIndexManager` 的扫描思路

## 6.2 建议新增模块

建议新增包：

- `app/src/main/java/com/github/tvbox/osc/ktv/`

推荐结构：

```text
ktv/
  bean/
  cache/
  data/
  parser/
  provider/
  player/
  ui/activity/
  ui/adapter/
  ui/dialog/
  util/
```

建议新增类：

- `ktv/provider/MediaLibraryProvider.java`
- `ktv/provider/LocalMediaLibraryProvider.java`
- `ktv/provider/WebDavMediaLibraryProvider.java`
- `ktv/parser/KtvSongNameParser.java`
- `ktv/data/KtvLibraryIndexManager.java`
- `ktv/data/KtvQueueManager.java`
- `ktv/bean/KtvSong.java`
- `ktv/bean/KtvMediaSource.java`
- `ktv/bean/KtvQueueItem.java`
- `ktv/ui/activity/KtvHomeActivity.java`
- `ktv/ui/activity/KtvSongListActivity.java`
- `ktv/ui/activity/KtvQueueActivity.java`

## 6.3 对现有模块的改造点

### `DriveActivity`

建议定位调整为：

- 保留“存储源管理”职责
- 不承担完整 KTV 点歌职责

可以增加：

- “加入到 KTV 歌库”动作
- “重新扫描”动作

### `HomeFolderIndexManager`

建议不要直接继续堆功能，而是将其升级或迁移为统一索引服务：

- 现有实现偏本地目录快捷索引
- KTV 需要支持多源统一扫描
- KTV 需要歌曲元数据字段，不只是文件路径

建议新增 `KtvLibraryIndexManager`，逐步替代其在 KTV 场景中的职责。

### `PlayActivity`

建议增加 `KTV_MODE` 启动参数。

KTV 模式下：

- 隐藏与影视点播无关的操作
- 显示歌曲信息和队列入口
- 监听播放完成后自动切换队列下一首

### `DrivePlayHelper`

建议新增：

- `playKtvSong(...)`
- `playKtvQueue(...)`

用于和普通 `VodInfo` 播放区分上下文。

## 7. 存储源专项方案

## 7.1 本地磁盘

实现建议：

- 继续基于 `File` 访问
- 使用已有存储权限逻辑
- 扫描指定根目录建立索引

优点：

- 实现成本最低
- 性能最好
- 播放最稳定

问题：

- Android 11+ 不同 ROM 对全盘访问兼容性不一致

## 7.2 外接磁盘

实现建议：

- 沿用本地文件访问模型
- 通过 `StorageRootFinder` 识别根路径
- 将外接盘作为一个特殊的本地媒体源处理

风险：

- 某些设备挂载点不固定
- 热插拔后路径可能失效
- 存在读权限变化问题

建议补充：

- 启动时检测根路径是否仍存在
- 歌库扫描前做可读性校验
- 失效后将媒体源标记为异常

## 7.3 WebDAV

实现建议：

- 浏览时仍然使用目录列举
- 扫描时使用递归 `PROPFIND` 建立本地缓存索引
- 播放时继续通过带鉴权头的 URL 直连播放器

关键问题：

- 大目录递归速度慢
- 网络波动导致扫描中断
- 特殊字符路径编码
- 认证过期

建议：

- 索引采用后台任务
- 扫描支持断点重试
- 按目录分批落库
- 对 URL 进行统一编码
- 记录最近扫描时间和错误原因

## 8. 索引策略建议

## 8.1 第一阶段

采用“全量扫描 + 本地缓存”。

特点：

- 简单直接
- 易于验证
- 适合中小型歌库

适用规模：

- 5000 首以内体验较好
- 10000 首以上需要关注扫描耗时与数据库查询优化

## 8.2 第二阶段

增加增量扫描：

- 根据 `lastModified`
- 根据文件数量变化
- 根据目录快照比对

## 8.3 搜索策略

建议支持以下字段模糊查询：

- `title`
- `artist`
- `fileName`
- `pinyin`
- `initials`

这会显著提升电视遥控器输入场景下的可用性。

## 9. 播放与队列方案

## 9.1 队列管理

建议新增 `KtvQueueManager` 统一维护：

- 当前歌曲
- 待播队列
- 播放历史
- 播放模式

数据既要有内存态，也建议有持久化态。

原因：

- App 被系统回收后可恢复队列
- 开机后可恢复上次点歌状态

## 9.2 自动连播

需要在播放器完成事件中接入队列调度。

逻辑建议：

1. 当前歌曲播放完成
2. 从队列取下一首
3. 生成播放上下文
4. 无缝切歌

## 9.3 失败容错

若下一首播放失败：

- 标记该歌曲播放失败
- Toast 或弹层提示
- 自动尝试队列下一首

避免因单个坏文件阻断整场点歌。

## 10. 推荐实施阶段

## 阶段 P0：最小可用版本

目标：

- 支持本地/外接盘/WebDAV 添加为 KTV 媒体源
- 扫描视频文件并生成歌库
- 搜索歌曲
- 立即播放

交付结果：

- 可用，但还不是完整 KTV

## 阶段 P1：标准点歌版本

目标：

- 歌曲加入队列
- 队列页
- 下一首/上一首
- 自动连播
- 收藏/最近播放

交付结果：

- 具备 KTV 基础体验

## 阶段 P2：体验增强版本

目标：

- 歌手分类
- 拼音/首字母筛选
- 插播
- 队列排序
- 歌词/封面支持

交付结果：

- 接近商用家用 KTV 体验

## 阶段 P3：高级能力

目标：

- 增量扫描
- WebDAV 大歌库优化
- 手机遥控点歌
- 局域网点歌接口

交付结果：

- 适合长期演进

## 11. 预计工作量

按单人开发估算：

- P0：3 到 5 天
- P1：5 到 8 天
- P2：5 到 10 天
- P3：按需求追加

如果需要兼顾多型号电视盒子兼容、外接盘热插拔和 WebDAV 大歌库优化，整体周期应再增加。

## 12. 风险与对策

### 风险 1：Android 存储权限兼容性

现象：

- 不同设备对 `MANAGE_EXTERNAL_STORAGE` 支持不一致

对策：

- 保留现有权限方案
- 增加不可访问路径校验与错误提示
- 必要时预留 SAF 方案作为后备路径选择机制

### 风险 2：WebDAV 扫描性能差

现象：

- 大目录递归慢
- 网络抖动导致失败

对策：

- 后台扫描
- 分批入库
- 错误恢复
- 显示扫描进度和状态

### 风险 3：文件名不规范导致搜索体验差

现象：

- 无法正确提取歌手和歌名

对策：

- 第一阶段允许只搜文件名
- 第二阶段补文件名解析规则
- 后续支持人工编辑元数据

### 风险 4：播放器与队列耦合不清

现象：

- 普通 VOD 与 KTV 模式逻辑互相污染

对策：

- 引入 `KTV_MODE`
- 通过独立 `QueueManager` 管理队列
- 尽量避免把 KTV 特有逻辑散落到现有 VOD 代码中

## 13. 验收标准

首个可交付版本建议满足：

- 能添加本地目录、外接盘目录、WebDAV 地址为歌库源
- 能完成扫描并生成歌库记录
- 能按关键词搜索歌曲
- 能加入点歌队列
- 能自动顺序播放下一首
- 能删除待播歌曲
- 播放失败时不会卡死整个队列
- 遥控器可完整操作主流程

## 14. 最终建议

建议采用“复用现有播放器与盘源浏览能力，新建独立 KTV 业务层”的方案，而不是直接把 `DriveActivity` 强行扩展成完整点歌系统。

原因很明确：

- 现有代码已经具备底层能力，重写成本没有必要
- KTV 与普通文件浏览的交互模式不同
- 分层后更容易继续支持 SMB、NAS、手机点歌等后续扩展

## 15. 推荐落地顺序

建议按以下顺序实施：

1. 新建 KTV 数据模型与数据库表
2. 抽象统一媒体源接口
3. 接入本地/外接盘/WebDAV 扫描
4. 做歌库搜索页
5. 做点歌队列
6. 改造 `PlayActivity` 支持 KTV 模式
7. 最后再补歌手、拼音、歌词、封面等增强能力

这个顺序风险最低，也最容易尽快看到可用结果。
