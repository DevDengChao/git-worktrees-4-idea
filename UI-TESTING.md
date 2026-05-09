# Git Worktrees 4 IDEA - UI 测试指南

基于 [intellij-ui-test-robot](https://github.com/JetBrains/intellij-ui-test-robot) 实现

## 快速开始

### 第一步：启动带 Robot Server 的 IDE

```powershell
# 在终端 1 中运行（会阻塞，保持运行）
.\gradlew runIdeForUiTests
```

这会启动一个 IntelliJ IDEA 实例，并在 `http://localhost:8082` 上运行 Robot Server。

### 第二步：运行 UI 测试

```powershell
# 在终端 2 中运行
.\gradlew test -Dgw4i.ui.tests=true --tests "*GitWorktreesUiTest*"
```

### 一键运行（后台启动 IDE）

```powershell
# PowerShell
Start-Process -NoNewWindow -FilePath ".\gradlew" -ArgumentList "runIdeForUiTests"
Start-Sleep -Seconds 60  # 等待 IDE 启动
.\gradlew test -Dgw4i.ui.tests=true --tests "*GitWorktreesUiTest*"
```

## 调试 UI 组件

启动 IDE 后，访问 **http://localhost:8082** 可以：

1. **查看 UI 组件树** - 显示当前 IDE 所有组件的层级结构
2. **调试 XPath** - 在浏览器中测试 XPath 定位器
3. **获取组件属性** - 查看 class、accessiblename 等属性

这对于编写和调试 UI 测试非常有用！

## 测试文件说明

默认 `.\gradlew test` 同时运行 JUnit 5 测试和 JUnit 4 / IntelliJ `LightPlatform4TestCase` 测试；构建中必须保留 `junit-vintage-engine`，否则 JUnit 4 风格测试会被 JUnit Platform 静默跳过。

| 文件 | 类型 | 运行条件 |
|------|------|----------|
| `GitWorktreesUiTest.kt` | UI 集成测试 | 需要运行 `runIdeForUiTests` |
| `GitWorktreesOperationsServiceTest.kt` | 单元测试 | 直接运行 `./gradlew test` |
| `GitWorktreesActionsTest.kt` | 单元测试 | 直接运行 `./gradlew test` |
| `GitWorktreesPanelTest.kt` | 单元测试 | 直接运行 `./gradlew test` |

## 编写 UI 测试示例

### 查找组件

```kotlin
// 按可访问名称查找
remoteRobot.find<ComponentFixture>(
    byXpath("//div[@accessiblename='Git Worktrees']")
)

// 按类名查找 worktree 表格
remoteRobot.find<ComponentFixture>(
    byXpath("//div[@class='JBTable' or @class='JTable']")
)

// 组合条件查找
remoteRobot.find<ComponentFixture>(
    byXpath("//div[@class='ActionLink' and @text='Show Git Worktrees']")
)
```

### 等待组件

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

### 模拟操作

```kotlin
// 点击
button.click()

// 输入文本
textField.text = "some text"

// 右键菜单
table.rightClick()

// 执行 JavaScript
component.callJs("component.getText();")
```

## CI/CD 集成

GitHub Actions 示例：

```yaml
name: UI Tests
on: [push, pull_request]

jobs:
  ui-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: 'jbr'
          java-version: '21'
      
      - name: Start IDE
        run: ./gradlew runIdeForUiTests &
      
      - name: Wait for IDE
        run: sleep 60
      
      - name: Run UI Tests
        run: ./gradlew test -Dgw4i.ui.tests=true --tests "*UiTest*"
```

## 常见问题

### Connection refused

确保 IDE 已启动并监听 8082 端口：
```powershell
netstat -ano | findstr 8082
```

### 找不到组件

1. 访问 http://localhost:8082 查看组件树
2. 使用正确的 XPath 语法
3. 增加 `waitFor` 超时时间

### 测试被跳过

UI 测试使用了 `@EnabledIfSystemProperty(named = "gw4i.ui.tests", matches = "true")`，
只有显式传入 `-Dgw4i.ui.tests=true` 时才会执行。正常运行 `./gradlew test` 时会自动跳过 UI 测试；需要跑 UI 测试时，先运行 `runIdeForUiTests`，再运行 `./gradlew test -Dgw4i.ui.tests=true`。
