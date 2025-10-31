# 游戏资源文件组织结构

## 📁 文件夹结构

```
src/main/resources/
├── gamedata/           # 游戏数据文件
│   ├── uno/           # UNO游戏数据
│   │   ├── cards.json # 卡牌数据
│   │   ├── config.yml # 游戏配置（YAML格式，支持注释）
│   │   └── rules/     # 游戏规则文档
│   │       └── basic_rules.md # 完整游戏规则
│   ├── chess/         # 象棋游戏数据（示例）
│   │   ├── pieces.json# 棋子数据
│   │   ├── config.yml # 游戏配置
│   │   └── rules/     # 游戏规则文档
│   └── ...            # 其他游戏类型
├── i18n/              # 国际化文本
│   ├── uno/           # UNO游戏文本
│   │   ├── zh_CN.json # 中文文本
│   │   ├── en_US.json # 英文文本（可选）
│   │   └── ...        # 其他语言
│   ├── chess/         # 象棋游戏文本（示例）
│   │   └── zh_CN.json # 中文文本
│   └── ...            # 其他游戏类型
└── ...
```

## 🎮 添加新游戏类型

当添加新的游戏类型时，请按照以下步骤：

### 1. 创建文件夹结构
```bash
mkdir src/main/resources/gamedata/{game_type}
mkdir src/main/resources/gamedata/{game_type}/rules
mkdir src/main/resources/i18n/{game_type}
```

### 2. 添加游戏数据文件
- `gamedata/{game_type}/cards.json` - 卡牌/棋子数据
- `gamedata/{game_type}/config.yml` - 游戏规则配置（推荐YAML格式）
- `gamedata/{game_type}/rules/basic_rules.md` - 游戏规则说明书

### 3. 添加国际化文件
- `i18n/{game_type}/zh_CN.json` - 中文文本
- `i18n/{game_type}/en_US.json` - 英文文本（可选）

### 4. 更新 GameDataManager
在 `GameDataManager.java` 中添加对应的加载方法：

```java
private void load{GameType}Data() throws IOException {
    loadCardsFromFile("{game_type}", "gamedata/{game_type}/cards.json");
    loadConfigFromFile("{game_type}", "gamedata/{game_type}/config.yml");
    loadTextsFromFile("{game_type}", "i18n/{game_type}/zh_CN.json");
}
```

## 📝 文件命名规范

- **游戏数据文件**: 使用通用名称（如 `cards.json`, `config.yml`）
- **配置文件**: 推荐使用YAML格式（`.yml`），支持注释，便于维护
- **规则文档**: 使用Markdown格式（`.md`），便于阅读和编辑
- **国际化文件**: 使用语言代码（如 `zh_CN.json`, `en_US.json`）
- **文件夹名称**: 使用游戏类型的小写英文名称

## 🔧 配置文件格式

### YAML配置文件优势
- ✅ 支持注释，便于说明配置项
- ✅ 层次结构清晰，易于阅读
- ✅ 支持多行文本和复杂数据结构
- ✅ 与Spring Boot完美集成

### 示例配置结构
```yaml
# 游戏基本信息
gameInfo:
  name: "游戏名称"
  version: "1.0"
  description: "游戏描述"

# 玩家限制
playerLimits:
  minPlayers: 2        # 最少玩家数
  maxPlayers: 8        # 最多玩家数

# 游戏规则
gameRules:
  # 详细的规则配置...
```

## 📚 规则文档规范

### 规则文档结构
1. **游戏概述** - 简单介绍游戏目标
2. **卡牌组成** - 详细说明卡牌类型和数量
3. **游戏流程** - 从开始到结束的完整流程
4. **功能牌详解** - 每种特殊卡牌的效果
5. **特殊规则** - 质疑、叠加等特殊情况
6. **获胜条件** - 明确的胜利条件
7. **新手提示** - 帮助新玩家快速上手
8. **常见错误** - 避免常见的规则误解

### 编写原则
- 🎯 **新手友好** - 使用简单易懂的语言
- 📖 **结构清晰** - 合理的章节划分
- 💡 **实用性强** - 包含实用的提示和示例
- ⚠️ **避免歧义** - 明确的规则表述

## 🔄 迁移说明

### 从JSON到YAML
原有的JSON配置文件可以转换为YAML格式：
```
gamedata/uno_config.json → gamedata/uno/config.yml
```

### 规则文档整合
- 将基础规则和高级规则合并为一个文件
- 去除过于复杂的变体规则
- 专注于核心游戏体验

## 🎉 新结构的优势

1. ✅ **按游戏类型清晰分类** - 便于管理和扩展
2. ✅ **YAML配置支持注释** - 提高配置文件的可维护性
3. ✅ **规则文档集中管理** - 便于玩家查阅和开发者维护
4. ✅ **标准化文件命名** - 避免文件名冲突
5. ✅ **新手友好的规则说明** - 降低学习门槛
6. ✅ **模块化设计** - 便于添加新游戏类型