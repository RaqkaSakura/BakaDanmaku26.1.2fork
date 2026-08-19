# Baka Danmaku Fabric 26.1.2

这是从 Baka Danmaku Fabric 1.21 迁移到 Minecraft 26.1.2 的客户端模组项目。

## 运行环境

- Minecraft 26.1.2
- Fabric Loader 0.19.2 或更高版本
- Fabric API 0.155.2+26.1.2 或更高的 26.1.2 兼容版本
- Java 25

## 构建

```powershell
$env:JAVA_HOME = 'Java 25 的安装目录'
.\gradlew.bat clean build
```

正式 JAR 位于 `build/libs/`，文件名不包含 `sources`。

## 实机测试

1. 将正式 JAR 和 Fabric API 放入 Minecraft 26.1.2 的 `mods` 目录。
2. 启动游戏并进入一次单人世界或服务器，模组会生成 `config/bakadanmaku/bilibili.json`。
3. 退出世界，将配置中的 `room.id` 改为直播间号，并将 `room.enable` 改为 `true`。
4. 再次进入世界，聊天栏应显示房间连接结果和匹配配置的直播消息。
5. 修改配置后可在世界内按 `B` 重新加载，无需重启客户端。
6. 分别验证远程服务器、单人世界、退出世界后重进，以及连续按 `B` 重载的场景。

默认使用游客鉴权。需要登录态时可按原配置格式填写 Cookie；不要把含 Cookie 的配置文件提交或发给他人。

### 昵称显示为 `***`

B 站会对游客连接返回的部分用户昵称进行脱敏，例如将“一只蝙蝠丶”显示成“一***”。这不是字符集乱码，模组无法从脱敏结果恢复原昵称。

如需显示完整昵称，请将浏览器中已登录 B 站账号的 Cookie 填入 `config/bakadanmaku/bilibili.json` 的 `room.cookie`。通常需要填写 `SESSDATA`、`DedeUserID`、`DedeUserID__ckMd5` 和 `bili_jct`；`buvid3`、`buvid4` 留空时模组会自动获取。保存后在游戏内按 `B` 重载。

Cookie 相当于账号登录凭据，只能保存在自己的电脑上，不要截图、提交到版本库或发送给他人。

### 显示模式和 HUD 设置

按 `O` 打开弹幕设置。显示模式可以选择“聊天栏”或“独立 HUD”；独立 HUD 模式下可以选择 16 色陶瓦窗口材质、背景透明度和 16 色羊毛弹幕框材质。打开“编辑 HUD 位置与大小”后，拖动窗口标题栏调整位置，拖动右下角调整大小；弹幕文本会根据窗口宽度自动换行。聊天栏模式下这些外观和布局选项会自动禁用。按“完成”保存设置，按 `B` 仍然只用于重载连接配置。

## 迁移说明

- 构建链更新为 Fabric Loom 1.16、Gradle 9.5.1 和 Java 25。
- 使用 Minecraft 26.1.2 官方映射。
- 使用 `ClientPlayConnectionEvents` 监听进入和离开世界，移除了服务端 `PlayerManager` Mixin。
- 按键 API 更新为 `KeyMapping` 和 `KeyMappingHelper`。
- 聊天消息通过客户端线程安全地写入聊天 HUD。
- 移除了与 Minecraft 26.1.2 内置 Netty 冲突的旧 Netty 4.1.77 打包。
- WebSocket 使用系统证书校验，并在关闭时回收事件循环和心跳任务。
- B 站 HTTP 请求和 WebSocket 握手增加了超时，避免长时间卡住连接任务。
