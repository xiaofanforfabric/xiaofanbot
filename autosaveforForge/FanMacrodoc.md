# FanMacro 宏自动化工具文档

## 目录

1. [简介](#简介)
2. [宏文件格式](#宏文件格式)
3. [主入口点](#主入口点)
4. [函数定义](#函数定义)
5. [命令列表](#命令列表)
6. [条件语句](#条件语句)
7. [示例代码](#示例代码)
8. [注意事项](#注意事项)

---

## 简介

FanMacro 是一个基于 Baritone API 的 Minecraft 自动化宏系统，允许玩家编写简单的文本文件来自动执行游戏中的各种任务。

### 宏文件位置

宏文件应放置在以下目录：
```
.minecraft/config/do/
```

宏文件必须是 `.txt` 格式，文件名即为宏名称。

### 注释

支持单行注释，使用 `//` 开头：
```
do #goto 0 0 0; // 这是注释
```

---

## 宏文件格式

### 基本结构

宏文件有两种格式：

**格式1：无主入口点（传统格式）**
```
do #goto 0 0 0;
do #mine iron_ore;
```

**格式2：带主入口点（推荐格式）**
```
fun name="goto" type= &;
    // 函数内容
    do #goto 0 0 0;
    
fan_main:
    do fun "goto";
    do #mine iron_ore;
```

---

## 主入口点

使用 `fan_main:` 作为宏的主入口点。当宏启动时，会从 `fan_main:` 之后的第一条命令开始执行。

**语法：**
```
fan_main:
    // 主程序命令
    do #goto 0 0 0;
    do fun "myFunction";
```

**特点：**
- `fan_main:` 之前可以定义函数
- `fan_main:` 之后是主程序流程
- 如果没有 `fan_main:`，则按顺序执行所有命令

---

## 函数定义

函数允许将代码块封装为可重用的单元。

### 语法

```
fun name="函数名" type= &;
    // 函数内容
    do #goto 0 0 0;
    do #mine iron_ore;
```

### 参数说明

- `name="函数名"`：函数名称，必须用双引号包裹
- `type= &`：可选参数
  - **不指定 `type`**：函数在主线程执行，会阻塞后续命令
  - **指定 `type= &`**：函数在后台执行，不阻塞主线程

### 调用函数

```
do fun "函数名";
```

### 示例

```
fun name="gotoHome" type= &;
    do #set allowBreak false;
    do #set allowPlace false;
    do #goto 0 85 -3062;
    do #set allowBreak true;
    do #set allowPlace true;

fan_main:
    do fun "gotoHome";
    do #mine iron_ore;
```

---

## 命令列表

### 1. do 命令

执行 Baritone 命令或原版 Minecraft 命令。

#### Baritone 命令（# 开头）

**语法：**
```
do #命令 参数;
```

**示例：**
```
do #goto 0 0 0;           // 移动到指定坐标
do #mine iron_ore;        // 挖掘铁矿石
do #stop;                 // 停止当前任务
do #set allowBreak true;  // 设置允许破坏方块
do #set allowPlace false; // 设置禁止放置方块
```

**阻塞命令：**
以下命令会阻塞宏执行，直到命令完成：
- `#goto`：等待到达目标位置（容差3格）
- `#mine`：等待挖掘完成
- `#explore`：等待探索完成
- `#farm`：等待种植/收获完成
- `#follow`：等待跟随完成

**非阻塞命令：**
- `#stop`：立即停止
- `#set`：立即设置

#### 原版 Minecraft 命令（/ 开头）

**语法：**
```
do /命令 参数;
```

**示例：**
```
do /home;        // 执行 /home 命令
do /spawn;       // 执行 /spawn 命令
do /tp 0 0 0;    // 执行 /tp 命令
```

---

### 2. check 命令

条件检查命令，如果条件满足则执行指定动作，否则继续执行下一个命令。

#### 语法1：检查物品（have）

```
check me have (item = 物品名, type = 工具类型, quantity = 数量), do 动作;
```

**参数说明：**
- `item`：物品名称（必需）
  - 非工具物品：使用原版注册名（不加 `minecraft:` 前缀），如 `diamond`、`raw_iron`
  - 工具物品：使用工具名称，如 `pickaxe`、`axe`
- `type`：工具类型（仅工具类物品，可选）
  - 可选值：`diamond`、`iron`、`golden`、`stone`、`wooden`、`netherite`
- `quantity`：数量（可选，默认1，范围1-64）

**示例：**
```
check me have (item = diamond, quantity = 10), do #goto 0 0 0;
check me have (item = pickaxe, type = diamond, quantity = 1), do #mine iron_ore;
```

**注意：**
- 非工具物品使用精确匹配，`diamond` 不会匹配 `diamond_pickaxe`
- 工具物品可以部分匹配，`pickaxe` 可以匹配 `diamond_pickaxe`

#### 语法2：检查没有物品（nothave）

```
check me nothave (item = 物品名), do 动作;
```

**参数说明：**
- `item`：物品名称（必需，仅支持此参数）
- 使用精确匹配，`diamond` 不会匹配 `diamond_pickaxe`

**示例：**
```
check me nothave (item = diamond), do #mine diamond_ore;
check me nothave (item = raw_iron), do #goto 0 0 0;
```

#### 语法3：检查位置

```
check me at = (x, y, z), do 动作;
```

**参数说明：**
- `(x, y, z)`：目标坐标
- 容差：±2 格

**示例：**
```
check me at = (0, 85, -3062), do end;  // 如果在目标位置，结束宏
check me at = (0, 0, 0), do #stop;     // 如果在目标位置，停止任务
```

#### 语法4：检查时间

```
check time = 时间刻, do 动作;
```

**参数说明：**
- `时间刻`：服务器世界时间（0-23999）
  - 0 = 6:00
  - 6000 = 12:00
  - 12000 = 18:00
  - 18000 = 0:00（午夜）
- 容差：±50 刻

**示例：**
```
check time = 11000, do /home;  // 如果时间到 11000 刻（下午5点），执行 /home
```

---

### 3. wait 命令

阻塞当前线程，等待指定时间或直到宏结束。

**语法：**
```
wait;        // 一直阻塞，直到宏结束
wait xs;     // 阻塞 x 秒
wait xm;     // 阻塞 x 分钟
wait xh;     // 阻塞 x 小时
```

**示例：**
```
wait 5s;     // 等待 5 秒
wait 10m;    // 等待 10 分钟
wait 1h;     // 等待 1 小时
wait;        // 一直等待，直到宏被停止
```

---

### 4. run 命令

立即启动另一个宏（独立执行，不阻塞当前宏）。

**语法：**
```
run name = "宏名称";
```

**示例：**
```
run name = "回家";
run name = "挖矿";
```

**注意：**
- 如果指定的宏不存在，会抛出 `NotFanMacroFound` 异常（会被捕获，不会崩溃）
- 启动的宏是独立执行的，不会阻塞当前宏
- 多个宏可以同时运行，但冲突的命令（如 `goto` 和 `mine`）会导致后启动的宏被停止

---

### 5. end 命令

结束当前执行上下文。

**语法：**
```
do end;
```

**说明：**
- 在 `if` 块中使用：只结束 `if` 分支，继续执行主宏流程
- 在函数中使用：只结束函数执行，继续执行主宏流程
- 在主宏中使用：结束整个宏

**示例：**
```
if me at = (0, 0, 0)
    do end;  // 结束 if 块，继续执行主宏
else
    do #goto 0 0 0;
```

---

## 条件语句

### if 语句

支持位置检查和时间检查。

#### 位置检查

**语法：**
```
if me at = (x, y, z)
    // if 块命令
    do #goto 0 0 0;
else
    // else 块命令（可选）
    do #stop;
end;
```

**容差：** ±2 格

**示例：**
```
if me at = (0, 85, -3062)
    do #set allowBreak false;
    do #set allowPlace false;
    do #goto 19 78 -3016;
    do #set allowBreak true;
    do #set allowPlace true;
else
    do end;
```

#### 时间检查

**语法：**
```
if time = 时间刻
    // if 块命令
else
    // else 块命令（可选）
end;

if time >= 时间刻
    // if 块命令
else
    // else 块命令（可选）
end;

if time <= 时间刻
    // if 块命令
else
    // else 块命令（可选）
end;
```

**时间刻说明：**
- 0-23999 刻 = 一天
- 0 刻 = 6:00
- 6000 刻 = 12:00
- 12000 刻 = 18:00
- 18000 刻 = 0:00（午夜）

**容差：** ±50 刻

**示例：**
```
fun name="sleep" type= &;
    if time = 11000
        do #stop;
        do #set allowBreak false;
        do #set allowPlace false;
        do #goto 0 85 -3062;
        do #set allowBreak true;
        do #set allowPlace true;
        do end;
    else
        do fun "sleep";
```

### if-else 执行流程

**重要：** `if` 块中的命令执行完毕后，**会继续执行主宏的下一个命令**，而不是直接结束整个宏。

**示例：**
```
fan_main:
    do fun "goto";
    do fun "sleep";      // if 块执行完毕后，会继续执行这里
    do #mine iron_ore;   // 然后执行这里

fun name="goto";
    if me at = (0, 85, -3062)
        do #set allowBreak false;
        do #set allowPlace false;
        do #goto 19 78 -3016;  // 阻塞等待到达
        do #set allowBreak true;
        do #set allowPlace true;
        // 执行完毕后，继续执行主宏的下一个命令（do fun "sleep"）
    else
        do end;
```

---

## 示例代码

### 示例1：简单挖矿宏

```
fan_main:
    do #goto 19 78 -3016;
    do #mine iron_ore;
```

### 示例2：带条件检查的挖矿宏

```
fan_main:
    do fun "goto";
    do fun "sleep";
    do #mine iron_ore;

fun name="goto";
    if me at = (0, 85, -3062)
        do #set allowBreak false;
        do #set allowPlace false;
        do #goto 19 78 -3016;
        do #set allowBreak true;
        do #set allowPlace true;
        do fun "sleep";
        do #mine iron_ore;
    else
        do end;

fun name="sleep" type= &;
    if time = 11000
        do #stop;
        do #set allowBreak false;
        do #set allowPlace false;
        do #goto 0 85 -3062;
        do #set allowBreak true;
        do #set allowPlace true;
        do end;
    else
        do fun "sleep";
```

### 示例3：使用 check 命令

```
fan_main:
    check me nothave (item = diamond), do #mine diamond_ore;
    check me have (item = diamond, quantity = 10), do #goto 0 0 0;
    check time = 11000, do /home;
    do #mine iron_ore;
```

### 示例4：使用 run 命令启动其他宏

```
fan_main:
    check me nothave (item = diamond), do run name = "挖钻石";
    check me nothave (item = iron_ingot), do run name = "挖铁";
    do #goto 0 0 0;
```

---

## 注意事项

### 1. 命令冲突

以下命令不能同时执行（来自不同宏）：
- `goto`、`mine`、`explore`、`farm`、`follow`

如果检测到冲突，后启动的宏会被自动停止。

### 2. 阻塞命令

以下命令会阻塞宏执行，直到完成：
- `#goto`：等待到达目标位置（容差3格）
- `#mine`：等待挖掘完成
- `#explore`、`#farm`、`#follow`：等待任务完成

### 3. 时间检查

- 使用**服务器世界时间**，不是单人存档时间
- 时间刻范围：0-23999
- 容差：±50 刻

### 4. 位置检查

- 容差：±2 格
- 坐标格式：`(x, y, z)`

### 5. 物品检查

- **非工具物品**：使用精确匹配
  - `diamond` 只匹配 `diamond`，不匹配 `diamond_pickaxe`
- **工具物品**：支持部分匹配
  - `pickaxe` 可以匹配 `diamond_pickaxe`
  - 需要指定 `type` 参数来区分材质

### 6. 函数执行

- **不指定 `type= &`**：函数在主线程执行，会阻塞后续命令
- **指定 `type= &`**：函数在后台执行，不阻塞主线程

### 7. 宏执行流程

- 如果宏包含 `if` 条件语句，宏会**循环执行**，持续检查条件
- 如果宏不包含 `if` 条件语句，宏会**一次性执行**所有命令

### 8. 死亡处理

- 在服务器模式下，如果玩家死亡，所有正在运行的宏和 Baritone 任务会**立即停止**

### 9. 错误处理

- 如果宏文件格式错误，会在日志中记录错误信息
- 如果启动不存在的宏，会抛出 `NotFanMacroFound` 异常（会被捕获）

### 10. 注释

- 支持单行注释：`// 这是注释`
- 注释可以放在行尾：`do #goto 0 0 0; // 移动到原点`

---

## 常见问题

### Q: 如何停止正在运行的宏？

A: 通过 Web 界面（端口 8079）或使用 Baritone 的 `#stop` 命令。

### Q: 宏文件应该放在哪里？

A: `.minecraft/config/do/` 目录下，文件名必须是 `.txt` 格式。

### Q: 如何查看所有可用的宏？

A: 通过 Web 界面（端口 8079）访问 `/status` 端点。

### Q: 为什么 `check me nothave (item = diamond)` 检查失败？

A: 确保使用的是精确匹配。如果背包里有 `diamond_pickaxe`，不会匹配 `diamond`。

### Q: 如何让函数在后台执行？

A: 在函数定义中添加 `type= &`：`fun name="函数名" type= &;`

### Q: `if` 块执行完毕后会怎样？

A: 会继续执行主宏的下一个命令，而不是结束整个宏。

---

## 更新日志

- **v1.0**：初始版本
  - 支持基本命令（do、if、fun）
  - 支持 Baritone 命令和原版命令
  - 支持函数定义和调用
  - 支持条件语句（位置、时间）
  - 支持 `check` 命令（物品、位置、时间）
  - 支持 `wait` 命令
  - 支持 `run` 命令
  - 支持 `fan_main:` 主入口点

---

## 技术支持

如有问题或建议，请查看日志文件或联系开发者。

**日志位置：** `.minecraft/logs/latest.log`

**Web 界面：** `http://localhost:8079`

