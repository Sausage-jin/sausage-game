# 🎮 LanTouBeiQiang — Java Mini-Game Collection

A Java Swing desktop game hub featuring **6 games** with LAN multiplayer, AI opponents, sound effects, and a unified dark theme.

> 烂头背枪 —— 一个用 Java Swing 编写的桌面小游戏合集，包含 6 款游戏，支持局域网联机、人机对战、音效与统一暗色主题。

---

## 📖 English

### Games Included

| Game | Mode | AI | LAN Multiplayer |
|------|------|:--:|:---------------:|
| **LanTouBeiQiang** (card-flip strategy) | 2 Players / vs AI | ✅ | ✅ |
| **Gomoku** (Five in a Row) | 2P / 3P / vs AI | ✅ | ✅ |
| **Tetris** | 2 Players (split-screen) | — | ✅ |
| **Draw & Guess** | 2 Players | — | ✅ |
| **Flappy Bird** | Single / 2 Players | — | — |
| **Dou Dizhu** (Fight the Landlord) | 3P / 4P / Online | ✅ | ✅ |

### Features

- **Unified dark theme** — all games share a consistent look & feel
- **LAN multiplayer** — host or join a room over the local network, with in-game chat
- **AI opponents** — Gomoku and Dou Dizhu include built-in AI engines
- **Sound & music** — background music per game plus UI/card sound effects (`.wav`)
- **In-game menu** — press `ESC` in any game to open the pause menu (resume / settings / quit)
- **Windows installer** — optional NSIS-style SFX installer built from `tools/`

### Requirements

- **JDK 11 or later** (JDK 21 recommended)
- Windows 10/11 (launch scripts are `.bat`; the game itself runs on any OS with Java)

### Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/<your-username>/LanTouBeiQiang.git
cd LanTouBeiQiang

# 2. Compile all sources
javac -encoding UTF-8 -d bin src\*.java src\com\doudizhu\Main.java src\com\doudizhu\engine\*.java src\com\doudizhu\model\*.java src\com\doudizhu\net\*.java src\com\doudizhu\ui\*.java

# 3. Run
java -cp bin Main
```

Or simply double-click **`启动游戏.bat`** (auto-compiles on first run, launches without a console window) / **`run.bat`** (shows console output).

### Build a Runnable JAR

```bash
jar cfm game.jar manifest.mf -C bin .
java -jar game.jar
```

### Project Structure

```
├── src/                        # Java source code
│   ├── Main.java               # Entry point (dark theme + launch HomeUI)
│   ├── HomeUI.java             # Main menu / room host / settings
│   ├── GameUI.java             # LanTouBeiQiang game (card-flip strategy)
│   ├── GameLogic.java          # LanTouBeiQiang rules engine
│   ├── GameAI.java             # LanTouBeiQiang AI
│   ├── BoardPanel.java         # LanTouBeiQiang board rendering
│   ├── GomokuUI.java           # Gomoku UI
│   ├── GomokuBoard.java        # Gomoku board logic
│   ├── GomokuAI.java           # Gomoku AI
│   ├── TetrisUI.java           # Tetris (2-player split-screen)
│   ├── DrawGuessUI.java        # Draw & Guess UI
│   ├── IdiomLibrary.java       # Idiom word bank
│   ├── BirdGame.java           # Flappy Bird
│   ├── NetPlay.java            # LAN networking (TCP rooms, chat)
│   ├── SoundManager.java       # Audio engine (javax.sound.sampled)
│   ├── UiKit.java              # Shared UI helpers (dialogs, buttons)
│   ├── Card.java / CardType.java       # Legacy card helpers
│   ├── DdzHostAdapter.java             # Dou Dizhu host bridge
│   ├── DdzRemoteController.java        # Dou Dizhu client controller
│   └── com/doudizhu/           # Dou Dizhu module (refactored)
│       ├── engine/             # GameEngine, FourPlayerEngine, RuleEngine, AIEngine
│       ├── model/              # Card, CardPlay, CardType, Player
│       ├── net/                # DdzCodec (protocol serializer)
│       └── ui/                 # GamePanel, CardPainter, SoundPlayer
├── image/                      # Game images (backgrounds, avatars, cards)
├── music/                      # Sound files (.wav — the game loads wav only)
├── tools/                      # Build scripts (installer, audio conversion)
├── manifest.mf                 # JAR manifest
├── app.ico                     # Application icon
├── run.bat                     # Compile + run (with console)
└── 启动游戏.bat                 # Compile + run (no console window)
```

### LAN Multiplayer

1. One player clicks **联机对战 → 创建房间 (Create Room)** and shares their LAN IP.
2. Other players click **联机对战 → 加入房间 (Join Room)** and enter the host's IP.
3. The host picks a game mode and clicks **开始 (Start)** — everyone enters the game automatically.
4. In-game chat is available in supported games.

### License

Released under a **Non-Commercial License** — see [LICENSE](LICENSE).

> ⚠️ This project (including source code, art assets, sound effects, UI design, and **gameplay/rules**) is licensed for **non-commercial use only**. Selling, monetizing, or using the same gameplay in commercial products without written permission from the author is strictly prohibited.

---

## 📖 中文

### 包含的游戏

| 游戏 | 模式 | 人机 | 局域网联机 |
|------|------|:--:|:----------:|
| **烂头背枪**（翻牌策略对战） | 双人 / 人机 | ✅ | ✅ |
| **五子棋** | 双人 / 三人 / 人机 | ✅ | ✅ |
| **俄罗斯方块** | 双人对战（分屏） | — | ✅ |
| **你画我猜** | 双人 | — | ✅ |
| **小鸟飞翔** | 单人 / 双人 | — | — |
| **斗地主** | 三人 / 四人 / 联机 | ✅ | ✅ |

### 功能特性

- **统一暗色主题** —— 所有游戏共享一致的界面风格
- **局域网联机** —— 局域网内创建/加入房间，支持游戏内聊天
- **人机对战** —— 五子棋、斗地主内置 AI 引擎
- **音效与背景音乐** —— 每款游戏独立 BGM，配有点击/出牌等音效（`.wav`）
- **游戏内菜单** —— 任意游戏中按 `ESC` 呼出菜单（继续 / 设置 / 退出）
- **Windows 安装包** —— 可选的 SFX 自解压安装程序（构建脚本位于 `tools/`）

### 环境要求

- **JDK 11 及以上**（推荐 JDK 21）
- Windows 10/11（启动脚本为 `.bat`；游戏本体可在任何装有 Java 的系统上运行）

### 快速开始

```bash
# 1. 克隆仓库
git clone https://github.com/<你的用户名>/LanTouBeiQiang.git
cd LanTouBeiQiang

# 2. 编译所有源码
javac -encoding UTF-8 -d bin src\*.java src\com\doudizhu\Main.java src\com\doudizhu\engine\*.java src\com\doudizhu\model\*.java src\com\doudizhu\net\*.java src\com\doudizhu\ui\*.java

# 3. 运行
java -cp bin Main
```

也可以直接双击 **`启动游戏.bat`**（首次运行自动编译，无控制台窗口）或 **`run.bat`**（显示控制台输出）。

### 打包可运行 JAR

```bash
jar cfm game.jar manifest.mf -C bin .
java -jar game.jar
```

### 项目结构

```
├── src/                        # Java 源代码
│   ├── Main.java               # 程序入口（暗色主题 + 启动主页）
│   ├── HomeUI.java             # 主菜单 / 联机房间 / 设置
│   ├── GameUI.java             # 烂头背枪游戏界面
│   ├── GameLogic.java          # 烂头背枪规则引擎
│   ├── GameAI.java             # 烂头背枪 AI
│   ├── BoardPanel.java         # 烂头背枪棋盘绘制
│   ├── GomokuUI.java           # 五子棋界面
│   ├── GomokuBoard.java        # 五子棋棋盘逻辑
│   ├── GomokuAI.java           # 五子棋 AI
│   ├── TetrisUI.java           # 俄罗斯方块（双人对战）
│   ├── DrawGuessUI.java        # 你画我猜界面
│   ├── IdiomLibrary.java       # 成语词库
│   ├── BirdGame.java           # 小鸟飞翔
│   ├── NetPlay.java            # 局域网联机（TCP 房间、聊天）
│   ├── SoundManager.java       # 音频引擎（javax.sound.sampled）
│   ├── UiKit.java              # 通用 UI 组件（弹窗、按钮）
│   ├── Card.java / CardType.java       # 旧版牌类辅助
│   ├── DdzHostAdapter.java             # 斗地主主机适配器
│   ├── DdzRemoteController.java        # 斗地主客户端控制器
│   └── com/doudizhu/           # 斗地主模块（重构版）
│       ├── engine/             # 引擎：GameEngine、FourPlayerEngine、RuleEngine、AIEngine
│       ├── model/              # 数据模型：Card、CardPlay、CardType、Player
│       ├── net/                # 网络：DdzCodec（协议编解码）
│       └── ui/                 # 界面：GamePanel、CardPainter、SoundPlayer
├── image/                      # 游戏图片（背景、头像、卡牌）
├── music/                      # 音频文件（.wav —— 游戏仅加载 wav）
├── tools/                      # 构建脚本（安装包、音频转换）
├── manifest.mf                 # JAR 清单文件
├── app.ico                     # 应用图标
├── run.bat                     # 编译 + 运行（带控制台）
└── 启动游戏.bat                 # 编译 + 运行（无控制台窗口）
```

### 局域网联机说明

1. 一名玩家点击 **联机对战 → 创建房间**，并将自己的局域网 IP 告知其他玩家。
2. 其他玩家点击 **联机对战 → 加入房间**，输入主机 IP。
3. 主机选择游戏模式后点击 **开始** —— 所有玩家自动进入游戏。
4. 支持的游戏内可使用聊天功能。

### 开源协议

本项目基于[非商业使用许可协议](LICENSE)开源。

> ⚠️ 本项目（含源代码、美术资源、音效、界面设计及**游戏玩法/规则**）仅供**非商业用途**使用。未经作者书面许可，严禁出售、盈利，或将相同玩法用于商业产品。