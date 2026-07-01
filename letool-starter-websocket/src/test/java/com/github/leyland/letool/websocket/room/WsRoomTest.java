package com.github.leyland.letool.websocket.room;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WsRoom 房间模型测试")
class WsRoomTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("应正确初始化 roomId 和 name")
        void shouldInitializeRoomIdAndName() {
            WsRoom room = new WsRoom("chat:room_1", "聊天室");
            assertEquals("chat:room_1", room.getRoomId());
            assertEquals("聊天室", room.getName());
        }

        @Test
        @DisplayName("name 为 null 时应使用 roomId")
        void nullNameShouldFallbackToRoomId() {
            WsRoom room = new WsRoom("chat:room_1", null);
            assertEquals("chat:room_1", room.getName());
        }

        @Test
        @DisplayName("应自动设置 createdAt")
        void shouldAutoSetCreatedAt() {
            WsRoom room = new WsRoom("room1", "test");
            assertNotNull(room.getCreatedAt());
        }

        @Test
        @DisplayName("新建房间应无成员")
        void newRoomShouldBeEmpty() {
            WsRoom room = new WsRoom("room1", "test");
            assertTrue(room.isEmpty());
            assertEquals(0, room.getMemberCount());
        }
    }

    @Nested
    @DisplayName("成员管理测试")
    class MemberManagementTests {

        @Test
        @DisplayName("addMember 应成功添加新成员")
        void addMemberShouldSucceed() {
            WsRoom room = new WsRoom("room1", "test");
            assertTrue(room.addMember("session1"));
            assertTrue(room.containsMember("session1"));
            assertEquals(1, room.getMemberCount());
        }

        @Test
        @DisplayName("重复添加同一成员应返回 false")
        void addDuplicateMemberShouldReturnFalse() {
            WsRoom room = new WsRoom("room1", "test");
            room.addMember("session1");
            assertFalse(room.addMember("session1"));
            assertEquals(1, room.getMemberCount());
        }

        @Test
        @DisplayName("removeMember 应成功移除成员")
        void removeMemberShouldSucceed() {
            WsRoom room = new WsRoom("room1", "test");
            room.addMember("session1");
            assertTrue(room.removeMember("session1"));
            assertFalse(room.containsMember("session1"));
            assertTrue(room.isEmpty());
        }

        @Test
        @DisplayName("移除不存在的成员应返回 false")
        void removeNonExistentMemberShouldReturnFalse() {
            WsRoom room = new WsRoom("room1", "test");
            assertFalse(room.removeMember("ghost"));
        }

        @Test
        @DisplayName("containsMember 应正确判断成员是否存在")
        void containsMemberShouldWork() {
            WsRoom room = new WsRoom("room1", "test");
            assertFalse(room.containsMember("session1"));
            room.addMember("session1");
            assertTrue(room.containsMember("session1"));
        }
    }

    @Nested
    @DisplayName("成员集合测试")
    class MembersTests {

        @Test
        @DisplayName("getMembers 应返回所有成员")
        void getMembersShouldReturnAllMembers() {
            WsRoom room = new WsRoom("room1", "test");
            room.addMember("s1");
            room.addMember("s2");
            room.addMember("s3");

            Set<String> members = room.getMembers();
            assertEquals(3, members.size());
            assertTrue(members.contains("s1"));
            assertTrue(members.contains("s2"));
            assertTrue(members.contains("s3"));
        }

        @Test
        @DisplayName("getMembers 返回的集合应不可修改")
        void getMembersShouldReturnUnmodifiableSet() {
            WsRoom room = new WsRoom("room1", "test");
            room.addMember("s1");
            Set<String> members = room.getMembers();
            assertThrows(UnsupportedOperationException.class, () -> members.add("s2"));
        }

        @Test
        @DisplayName("getMemberCount 应返回正确数量")
        void getMemberCountShouldReturnCorrectCount() {
            WsRoom room = new WsRoom("room1", "test");
            assertEquals(0, room.getMemberCount());
            room.addMember("s1");
            assertEquals(1, room.getMemberCount());
            room.addMember("s2");
            assertEquals(2, room.getMemberCount());
            room.removeMember("s1");
            assertEquals(1, room.getMemberCount());
        }

        @Test
        @DisplayName("isEmpty 应正确反映房间状态")
        void isEmptyShouldReflectRoomState() {
            WsRoom room = new WsRoom("room1", "test");
            assertTrue(room.isEmpty());
            room.addMember("s1");
            assertFalse(room.isEmpty());
            room.removeMember("s1");
            assertTrue(room.isEmpty());
        }
    }

    @Nested
    @DisplayName("扩展属性测试")
    class AttributeTests {

        @Test
        @DisplayName("setAttribute/getAttribute 应正确存取")
        void setAndGetAttributeShouldWork() {
            WsRoom room = new WsRoom("room1", "test");
            room.setAttribute("maxCapacity", 100);
            room.setAttribute("type", "chat");

            assertEquals(100, (int) room.getAttribute("maxCapacity"));
            assertEquals("chat", room.getAttribute("type"));
        }

        @Test
        @DisplayName("getAttribute 不存在的 key 应返回 null")
        void getAttributeAbsentKeyShouldReturnNull() {
            WsRoom room = new WsRoom("room1", "test");
            assertNull(room.getAttribute("nonexistent"));
        }

        @Test
        @DisplayName("getAttributes 应返回可修改的 Map")
        void getAttributesShouldReturnModifiableMap() {
            WsRoom room = new WsRoom("room1", "test");
            room.getAttributes().put("key", "value");
            assertEquals("value", room.getAttribute("key"));
        }
    }

    @Nested
    @DisplayName("equals/hashCode 测试")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("相同 roomId 的房间应相等")
        void sameRoomIdShouldBeEqual() {
            WsRoom r1 = new WsRoom("room1", "A");
            WsRoom r2 = new WsRoom("room1", "B");
            assertEquals(r1, r2);
            assertEquals(r1.hashCode(), r2.hashCode());
        }

        @Test
        @DisplayName("不同 roomId 的房间应不相等")
        void differentRoomIdShouldNotBeEqual() {
            WsRoom r1 = new WsRoom("room1", "test");
            WsRoom r2 = new WsRoom("room2", "test");
            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("同一对象应相等")
        void sameObjectShouldBeEqual() {
            WsRoom room = new WsRoom("room1", "test");
            assertEquals(room, room);
        }

        @Test
        @DisplayName("与 null 比较应返回 false")
        void compareToNullShouldReturnFalse() {
            assertNotEquals(new WsRoom("room1", "test"), null);
        }

        @Test
        @DisplayName("与不同类对象比较应返回 false")
        void compareToDifferentClassShouldReturnFalse() {
            assertNotEquals(new WsRoom("room1", "test"), "room1");
        }
    }

    @Nested
    @DisplayName("toString 测试")
    class ToStringTests {

        @Test
        @DisplayName("toString 应包含关键字段")
        void toStringShouldContainKeyFields() {
            WsRoom room = new WsRoom("room1", "测试房间");
            room.addMember("s1");
            String str = room.toString();
            assertTrue(str.contains("room1"));
            assertTrue(str.contains("测试房间"));
            assertTrue(str.contains("1"));
            assertTrue(str.contains("WsRoom"));
        }
    }
}
