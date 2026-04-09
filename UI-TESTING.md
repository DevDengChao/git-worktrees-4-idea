# Git Worktrees 4 IDEA - UI 测试指南

## 快速开始

### 方法 1: 手动 UI 测试（推荐用于开发阶段）

```powershell
# 终端 1: 启动带 robot-server 的 IDE
.\gradlew runIdeForUiTests

# 终端 2: 运行 UI 测试
.\gradlew test --tests "*GitWorktreesUiTest*"
```

### 方法 2: 使用浏览器查看 UI 结构

启动 IDE 后访问: http://localhost:8082

这个页面显示当前 IDE 的 UI 组件层级，可用于：
- 查找组件的 XPath
- 调试 UI 测试定位器
- 了解组件结构

## UI 测试架构

### 测试文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `GitWorktreesUiTest.kt` | 集成 UI 测试 | 使用 RemoteRobot 测试真实 UI |
| `GitWorktreesOperationsServiceTest.kt` | 单元测试 | 测试服务层逻辑 |
| `GitWorktreesActionsTest.kt` | 单元测试 | 测试 Action 启用条件 |
| `GitWorktreesPanelTest.kt` | 单元测试 | 测试面板数据和 DataKey |

### RemoteRobot 原理

```
┌─────────────┐         HTTP         ┌──────────────────┐
│  Test Code  │ ──────────────────►   │  IDE + Robot     │
│  (JUnit)    │ ◄──────────────────   │  Server (:8082)  │
└─────────────┘                       └──────────────────┘
     发送指令:                              执行:
     - click()                          - 查找组件
     - text = "..."                     - 输入文本
     - rightClick()                     - 右键菜单
     - callJs("...")                    - 执行 JS
```

## 编写 UI 测试示例

### 查找组件

```kotlin
// 按 XPath 查找
remoteRobot.find<ComponentFixture>(
    byXpath("//div[@accessiblename='Git Worktrees']")
)

// 按类名查找
remoteRobot.find<ComponentFixture>(
    byXpath("//div[@class='JBList']")
)
```

### 等待组件出现

```kotlin
waitFor(Duration.ofSeconds(10)) {
    try {
        remoteRobot.find<ComponentFixture>(byXpath("//div[@class='MyComponent']"))
        true
    } catch (_: Exception) {
        false
    }
}
```

### 模拟用户操作

```kotlin
// 点击
button.click()

// 输入文本
textField.text = "some text"

// 右键菜单
list.rightClick()

// 执行 Action
remoteRobot.action("Find Action").run()
```

## CI/CD 集成

GitHub Actions 工作流示例:

```yaml
# .github/workflows/run-ui-tests.yml
name: Run UI Tests
on: [push, pull_request]

jobs:
  ui-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'jbr'
          java-version: '21'
      
      - name: Start IDE for UI Tests
        run: ./gradlew runIdeForUiTests &
      
      - name: Wait for IDE
        run: sleep 60
      
      - name: Run UI Tests
        run: ./gradlew test --tests "*UiTest*"
```

## 常见问题

### Q: Connection refused 错误

确保 IDE 已启动并监听 8082 端口：
```powershell
netstat -ano | findstr 8082
```

### Q: 找不到组件

1. 访问 http://localhost:8082 查看组件树
2. 使用浏览器开发者工具检查 XPath
3. 增加 waitFor 超时时间

### Q: 对话框阻塞测试

使用 `TestDialogManager` 模拟对话框选择：
```kotlin
TestDialogManager.setTestDialog(TestDialog { Messages.YES })
```

## 运行所有测试

```powershell
# 单元测试（快速，无需 IDE）
.\gradlew test --tests "*Test*" --exclude-test "*UiTest*"

# UI 测试（需要 IDE 运行）
.\gradlew runIdeForUiTests &
.\gradlew test --tests "*UiTest*"
```
