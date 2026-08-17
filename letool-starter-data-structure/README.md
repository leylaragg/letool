# letool-starter-data-structure

业务结构模块，提供不可变策略注册表、决策链（消除深层 if-else）、泛型树节点以及单向/双向链表等通用能力。
模块不注册 Bean、不读取配置属性，引入后可以直接使用。结构化错误统一复用 `letool-starter-exception`。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
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

// 4. 保存头节点后链式追加并遍历
LinkedNode<String> head = LinkedNode.of("a");
head.next("b").next("c");
head.forEach(System.out::println);
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

`build` 采用严格默认契约：节点不能为空、ID 必须非空且唯一、父节点必须存在、父链不能成环。
所有结构校验会在回填 `children` 之前完成，因此脏数据不会留下只构建了一部分的树。构建成功后，
每个节点会获得独立、可修改的子节点列表，根节点和同级节点顺序与输入列表一致。

构建器会修改传入节点的 `children`。不希望修改业务实体时使用下面的 `buildSimple` 包装方式。

**方式二：使用 `SimpleTreeNode` 包装（无需修改实体）：**

```java
List<SimpleTreeNode<Dept>> tree = TreeBuilder.buildSimple(
    deptList, Dept::getId, Dept::getParentId);
```

业务明确允许把父节点缺失的数据降级为根节点时，必须显式选择策略，不再静默丢弃孤儿节点：

```java
List<Dept> tree = TreeBuilder.build(deptList, TreeOrphanPolicy.AS_ROOT);

List<SimpleTreeNode<Dept>> wrappedTree = TreeBuilder.buildSimple(
    deptList,
    Dept::getId,
    Dept::getParentId,
    TreeOrphanPolicy.AS_ROOT
);
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

遍历、查询和统计均采用非递归实现，可处理万级深树。运行时发现环、空子节点元素或同一节点对象
被多个父节点重复引用时，会抛出 `DATA_STRUCTURE_007`，不会无限循环。`getAncestors` 同样会拒绝
重复 ID、缺失父节点和父链环，不再静默覆盖或截断祖先链。

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
// 保存头节点，再从返回的后继继续追加
LinkedNode<String> head = LinkedNode.of("a");
LinkedNode<String> tail = head.next("b").next("c");

// Consumer 遍历
head.forEach(System.out::println);

// for-each 遍历（实现 Iterable）
for (String s : head) {
    System.out.println(s);
}

// 统计
int size = head.count();
```

`next` 和 `nextNode` 都返回新连接的后继节点；解除连接使用 `setNext(null)`。所有连接入口都会拒绝
自环、候选链内部环以及包含当前节点的反向连接，遍历器还会防御外部反序列化或不受信任子类带入的
损坏环形链路。节点采用对象身份相等语义，比较数据内容请显式使用 `getData()`。

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

双向节点覆盖了继承的 `next`、`nextNode` 和 `setNext`：便捷入口始终创建双向节点，替换或解除后继
会同步维护旧、新节点的 `prev`。已有其他前驱的节点不能被静默抢占，应先在原前驱上解除连接；
普通 `LinkedNode` 也不能混入双向链。`appendNode` 返回被追加节点，可继续链式构建。

## 稳定错误码

| 错误码 | 含义 |
|---|---|
| `DATA_STRUCTURE_001` | 构建器、规则或策略注册参数无效 |
| `DATA_STRUCTURE_002` | 策略键重复注册 |
| `DATA_STRUCTURE_003` | 必需策略未找到，或显式替换目标不存在 |
| `DATA_STRUCTURE_004` | 决策链没有命中规则且没有默认动作 |
| `DATA_STRUCTURE_005` | 树节点 ID 重复 |
| `DATA_STRUCTURE_006` | 树节点的父节点不存在 |
| `DATA_STRUCTURE_007` | 树存在环或重复对象引用 |
| `DATA_STRUCTURE_008` | 链表连接关系不符合双向拓扑等约束 |
| `DATA_STRUCTURE_009` | 链表中检测到环 |

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
- `TreeBuilder.build` 不再静默忽略孤儿节点，也不再接受重复 ID 或父链环。确需把孤儿提升为根时，
  显式传入 `TreeOrphanPolicy.AS_ROOT`。
- `TreeBuilder` 在成功后会给每个节点回填独立、可修改的子节点列表；校验失败时不会修改节点。
- `TreeUtil.maxDepth`、`countNodes` 等操作改为非递归实现，并统一拒绝环和重复对象引用。
- `LinkedNode.nextNode` 与 `DoublyLinkedNode.appendNode` 现在返回被连接节点，便于连续追加。
- `LinkedNode` 和 `DoublyLinkedNode` 从数据值相等改为对象身份相等；旧代码应改为比较 `getData()`。
- 双向链表不再允许混入普通单向节点，也不再允许用 `setPrev` 制造不对称连接。节点已有前驱时，
  必须先解除旧连接再挂到新前驱。
