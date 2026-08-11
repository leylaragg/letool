# letool-starter-data-structure

业务结构模块，提供不可变策略注册表、决策链（消除深层 if-else）、泛型树节点以及单向/双向链表等通用能力。
模块不注册 Bean、不读取配置属性，引入后可以直接使用。结构化错误统一复用 `letool-starter-exception`。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-data-structure</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始

引入依赖后直接使用，无需任何配置。以下用 4 个典型场景展示核心能力：

```java
// 1. 注册支付策略并按渠道选择
StrategyRegistry<PayChannel, PayStrategy> strategies = StrategyRegistry
    .<PayChannel, PayStrategy>builder()
    .register(PayChannel.ALIPAY, alipayStrategy)
    .register(PayChannel.WECHAT, wechatStrategy)
    .build();
PayResult payResult = strategies.getRequired(request.getChannel()).pay(request);

// 2. 从数据库查出部门列表，构建树
List<Dept> tree = TreeBuilder.build(deptList);

// 3. 消除复杂的 if-else
String routeResult = DecisionChain.<Order, String>builder()
    .when(o -> o.getAmount() > 10000, o -> "大额订单")
    .otherwise(o -> "普通订单")
    .build()
    .execute(order);

// 4. 链式构建链表并遍历
LinkedNode.of("a").next("b").next("c").forEach(System.out::println);
```

## 核心 API 示例

### 1. 不可变策略注册表 StrategyRegistry

Letool 不规定统一的策略方法签名，业务可以直接注册自己的接口：

```java
public interface PayStrategy {
    PayResult pay(PayRequest request);
}

StrategyRegistry<PayChannel, PayStrategy> registry = StrategyRegistry
    .<PayChannel, PayStrategy>builder()
    .register(PayChannel.ALIPAY, new AlipayStrategy())
    .register(PayChannel.WECHAT, new WechatStrategy())
    .build();

// 外部输入允许未命中时使用 Optional
Optional<PayStrategy> optional = registry.find(request.getChannel());

// 业务必须存在对应策略时使用严格入口
PayStrategy strategy = registry.getRequired(request.getChannel());
PayResult result = strategy.pay(request);
```

`register` 默认拒绝重复键，不会静默覆盖已有实现。需要替换内置策略时必须明确调用 `replace`：

```java
StrategyRegistry<PayChannel, PayStrategy> customized = StrategyRegistry
    .builder(registry)
    .replace(PayChannel.ALIPAY, companyAlipayStrategy)
    .build();
```

`keys()` 和 `asMap()` 保持注册顺序且不可修改；每次 `build()` 都会复制构建器状态，后续继续修改构建器
不会影响已经创建的注册表。注册表结构可以安全并发读取，注册进去的策略对象仍应由业务保证无状态或线程安全。

运行期间需要动态注册、注销或热替换的 Job/WebSocket 等场景具有额外生命周期语义，应继续使用对应模块
提供的专用注册器，不要为了复用而绕过其领域校验。

### 2. 泛型树（TreeNode + TreeBuilder + TreeUtil）

**方式一：实体实现 `TreeNode` 接口（推荐）：**

```java
public class Dept implements TreeNode<Dept> {
    private Long id;
    private Long parentId;
    private String name;
    private List<Dept> children;

    @Override public Object getId() { return id; }
    @Override public Object getParentId() { return parentId; }
    @Override public List<Dept> getChildren() { return children; }
    @Override public void setChildren(List<Dept> children) { this.children = children; }
    // getters / setters ...
}

// 从平列表构建树
List<Dept> deptList = deptMapper.selectAll();
List<Dept> tree = TreeBuilder.build(deptList);
```

**方式二：使用 `SimpleTreeNode` 包装（无需修改实体）：**

```java
List<SimpleTreeNode<Dept>> tree = TreeBuilder.buildSimple(
    deptList, Dept::getId, Dept::getParentId);
```

**树遍历与操作（`TreeUtil`）：**

```java
// 遍历
TreeUtil.traversePreOrder(root, node -> System.out.println(node.getId()));
TreeUtil.traverseLevelOrder(root, node -> process(node));
TreeUtil.traversePostOrder(root, node -> cleanup(node));

// 查找
Optional<Dept> found = TreeUtil.findFirst(root, n -> "IT部".equals(n.getName()));

// 收集叶子节点 / 展平
List<Dept> leaves = TreeUtil.collectLeaves(root);
List<Dept> flatList = TreeUtil.flatten(root);

// 祖先查询（从平列表追踪）
List<Dept> ancestors = TreeUtil.getAncestors(allDepts, targetDept);

// 统计
int depth = TreeUtil.maxDepth(root);
int nodes = TreeUtil.countNodes(root);
```

### 3. 决策链（DecisionChain -- 消除 if-else）

**编程式链式构建：**

```java
DecisionChain<Order, String> chain = DecisionChain.<Order, String>builder()
    .when(o -> o.getAmount() > 10000, o -> "大额订单，走风控流程")
    .when(o -> o.isVip(),          o -> "VIP客户，走优先通道")
    .when(o -> o.getType() == 1,   o -> "普通订单，走标准流程")
    .otherwise(o -> "默认流程")
    .build();

String result = chain.execute(order);

// 启动诊断，不暴露内部规则对象
int ruleCount = chain.size();
boolean hasFallback = chain.hasDefault();
```

`DecisionChain` 按注册顺序执行首个命中的规则。业务决策链建议显式配置
`otherwise`；如果所有 `when` 都未命中且没有配置 `otherwise`，`execute`
会抛出错误码为 `DATA_STRUCTURE_004` 的 `DataStructureException`，避免用 `null` 掩盖规则漏配。
异常不会拼接上下文对象，避免订单、用户等业务数据进入日志或响应。用户条件和动作抛出的业务异常
保持原始类型传播，不会被决策链无差别包装。

**快捷单规则模式：**

```java
DecisionChain<String, Integer> chain = DecisionChain.of(Integer::parseInt);
Integer val = chain.execute("123");
```

### 4. 单向链表（LinkedNode）

```java
// 链式构建
LinkedNode<String> head = LinkedNode.of("a").next("b").next("c");

// Consumer 遍历
head.forEach(System.out::println);

// for-each 遍历（实现 Iterable）
for (String s : head) {
    System.out.println(s);
}

// 统计
int size = head.count();
```

### 5. 双向链表（DoublyLinkedNode）

```java
// 构建
DoublyLinkedNode<String> head = DoublyLinkedNode.of("a");
DoublyLinkedNode<String> mid = head.append("b");
DoublyLinkedNode<String> tail = mid.append("c");

// 正向遍历
head.forEach(System.out::println);  // a -> b -> c

// 反向遍历
tail.forEachReverse(System.out::println);  // c -> b -> a

// 导航
DoublyLinkedNode<String> first = tail.head();
DoublyLinkedNode<String> last = head.tail();

// 插入
DoublyLinkedNode<String> newHead = head.prepend("0");
```

## 稳定错误码

| 错误码 | 含义 |
|---|---|
| `DATA_STRUCTURE_001` | 构建器、规则或策略注册参数无效 |
| `DATA_STRUCTURE_002` | 策略键重复注册 |
| `DATA_STRUCTURE_003` | 必需策略未找到，或显式替换目标不存在 |
| `DATA_STRUCTURE_004` | 决策链没有命中规则且没有默认动作 |

## 2.0 迁移说明

- `DecisionChain` 的主入口和泛型签名保持不变；构建失败和未命中异常由 JDK 裸异常统一迁移为
  `DataStructureException`。
- 未命中异常不再包含上下文 `toString()` 内容。依赖旧异常文本的代码应改为判断稳定错误码。
- 旧 `DataStructureException(String)` 和 `DataStructureException(String, Throwable)` 构造器已删除，
  模块内部及业务代码应使用错误码和对应工厂，不再创建无结构异常。
- 删除了空的 `DataStructureAutoConfiguration` 及其导入清单。原配置没有创建 Bean、没有读取属性，
  因此无需提供替代配置；模块引入后仍可直接使用全部 API。
- 模块不再直接声明 tool、Spring Boot 或 SLF4J 依赖，只直接依赖统一异常模块；异常模块仍可能传递
  Spring 国际化和自动配置所需依赖。
