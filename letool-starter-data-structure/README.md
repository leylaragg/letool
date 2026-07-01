# letool-starter-data-structure

数据结构模块，提供泛型树节点、决策链（消除深层 if-else）以及单向/双向链表等通用数据结构，纯工具库，无配置，开箱即用。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-data-structure</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始

引入依赖后直接使用，无需任何配置。以下用 3 个典型场景展示核心能力：

```java
// 1. 从数据库查出部门列表，构建树
List<Dept> tree = TreeBuilder.build(deptList);

// 2. 消除复杂的 if-else
String result = DecisionChain.<Order, String>builder()
    .when(o -> o.getAmount() > 10000, o -> "大额订单")
    .otherwise(o -> "普通订单")
    .execute(order);

// 3. 链式构建链表并遍历
LinkedNode.of("a").next("b").next("c").forEach(System.out::println);
```

## 核心 API 示例

### 1. 泛型树（TreeNode + TreeBuilder + TreeUtil）

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

### 2. 决策链（DecisionChain -- 消除 if-else）

**编程式链式构建：**

```java
DecisionChain<Order, String> chain = DecisionChain.<Order, String>builder()
    .when(o -> o.getAmount() > 10000, o -> "大额订单，走风控流程")
    .when(o -> o.isVip(),          o -> "VIP客户，走优先通道")
    .when(o -> o.getType() == 1,   o -> "普通订单，走标准流程")
    .otherwise(o -> "默认流程")
    .build();

String result = chain.execute(order);
```

**快捷单规则模式：**

```java
DecisionChain<String, Integer> chain = DecisionChain.of(Integer::parseInt);
Integer val = chain.execute("123");
```

### 3. 单向链表（LinkedNode）

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

### 4. 双向链表（DoublyLinkedNode）

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
