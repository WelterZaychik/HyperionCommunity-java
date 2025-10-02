package xyz.Brownie.component;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NettyWebSocketServer {
    private static final int MAX_MESSAGE_LENGTH = 65536;
    private static final int MAX_OFFLINE_MESSAGES = 100;
    private static final int MAX_HISTORY_MESSAGES = 100;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    // 存储用户连接和信息
    public static final Map<String, UserInfo> userConnections = new ConcurrentHashMap<>();
    // 存储所有消息记录
    private static final List<Message> messageStore = new CopyOnWriteArrayList<>();
    // 存储离线消息
    private static final Map<String, List<Message>> offlineMessages = new ConcurrentHashMap<>();
    // 新增：存储用户通知 - 修改为按发送者分组
    private static final Map<String, Map<String, Notification>> userNotifications = new ConcurrentHashMap<>();

    @Data
    @AllArgsConstructor
    public static class UserInfo {
        private ChannelHandlerContext ctx;
        private String username;
    }

    @Data
    @AllArgsConstructor
    public static class Message {
        private String id;
        private String from;
        private String fromName;
        private String to;
        private String toName;
        private String text;
        private long timestamp;
        private boolean delivered;
        private String avatar; // 添加头像字段
    }

    @Data
    @AllArgsConstructor
    public static class Notification {
        private String id;
        private String from;
        private String fromName;
        private String text;
        private long timestamp;
        private String avatar; // 添加头像字段
    }

    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(MAX_MESSAGE_LENGTH));
                        pipeline.addLast(new WebSocketServerProtocolHandler("/imserver"));
                        pipeline.addLast(new WebSocketFrameHandler());
                    }
                });

        channel = bootstrap.bind(9292).sync().channel();
        log.info("Netty WebSocket 服务启动，端口：9292");
    }

    @PreDestroy
    public void stop() {
        if (channel != null) {
            channel.close();
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        log.info("Netty WebSocket 服务关闭");
    }

    private class WebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
            String message = frame.text();
            try {
                JSONObject obj = JSONUtil.parseObj(message);
                processMessage(ctx, obj);
            } catch (Exception e) {
                log.error("解析消息失败: {}", message, e);
                sendErrorResponse(ctx, "Invalid message format");
            }
        }

        private void processMessage(ChannelHandlerContext ctx, JSONObject obj) {
            String type = obj.getStr("type", "message");

            switch (type) {
                case "register":
                    handleRegisterMessage(ctx, obj);
                    break;
                case "getHistory":
                    handleHistoryRequest(ctx, obj);
                    break;
                case "getNotifications":
                    handleNotificationsRequest(ctx, getAccountFromMessage(obj));
                    break;
                case "markNotificationRead":
                    handleMarkNotificationRead(obj, getAccountFromMessage(obj));
                    break;
                case "message":
                    handleChatMessage(ctx, obj);
                    break;
                case "deleteHistory":
                    handleDeleteHistoryRequest(ctx, obj);
                    break;
                default:
                    sendErrorResponse(ctx, "Unsupported message type: " + type);
            }
        }
        
        private void handleDeleteHistoryRequest(ChannelHandlerContext ctx, JSONObject obj) {
            String account = obj.getStr("account");
            String withAccount = obj.getStr("withAccount");

            if (account == null || withAccount == null) {
                sendErrorResponse(ctx, "Both account and withAccount are required");
                return;
            }

            // 删除与这两个账户相关的所有消息记录
            messageStore.removeIf(msg -> 
                (msg.getFrom().equals(account) && msg.getTo().equals(withAccount)) ||
                (msg.getFrom().equals(withAccount) && msg.getTo().equals(account))
            );

            // 删除相关的通知
            Map<String, Notification> userNotificationsMap = userNotifications.get(account);
            if (userNotificationsMap != null) {
                userNotificationsMap.remove(withAccount);
            }

            // 发送成功响应 - 使用sendMessage代替sendSuccessResponse，避免添加Response后缀
            JSONObject response = new JSONObject();
            response.put("type", "deleteHistory");
            response.put("status", "success");
            response.put("message", "History messages deleted successfully");
            sendMessage(response.toString(), ctx);
        }

        private String getAccountFromMessage(JSONObject obj) {
            if (obj.containsKey("account")) {
                return obj.getStr("account");
            }
            if (obj.containsKey("from")) {
                return obj.getStr("from");
            }
            return null;
        }

        private void handleRegisterMessage(ChannelHandlerContext ctx, JSONObject obj) {
            String account = obj.getStr("account");
            String username = obj.getStr("username", account);

            if (account == null || account.isEmpty()) {
                sendErrorResponse(ctx, "Account is required");
                return;
            }

            userConnections.put(account, new UserInfo(ctx, username));
            log.info("用户[{}]已连接", account);

            sendOnlineUsersList();
            deliverOfflineMessages(account);
            sendSuccessResponse(ctx, "register", "Connection established");
        }

        private void handleChatMessage(ChannelHandlerContext ctx, JSONObject obj) {
            String from = obj.getStr("from");
            String to = obj.getStr("to");
            String text = obj.getStr("text");
            String avatar = obj.getStr("avatar", ""); // 获取头像信息，如果没有则使用空字符串

            if (from == null || to == null || text == null) {
                sendErrorResponse(ctx, "Missing required fields (from, to, text)");
                return;
            }

            if (text.length() > 1000) {
                sendErrorResponse(ctx, "Message too long (max 1000 characters)");
                return;
            }

            Message message = createMessage(from, to, text, avatar);
            storeMessage(message);

            // 创建并存储通知 - 这将确保接收者能在消息列表中看到消息
            Notification notification = new Notification(
                    message.getId(),
                    message.getFrom(),
                    message.getFromName(),
                    message.getText(),
                    message.getTimestamp(),
                    message.getAvatar() // 传递头像信息
            );
            storeNotification(to, notification);

            // 确保消息列表更新 - 直接添加到对方的消息记录中
            deliverMessage(message);
            // 发送通知提醒，确保消息列表弹出
            sendNotificationAlert(to, notification);

            sendSuccessResponse(ctx, "message", "Message sent");
        }

        private Message createMessage(String from, String to, String text, String avatar) {
            String fromName = getUserName(from);
            String toName = getUserName(to);

            return new Message(
                    generateMessageId(from),
                    from,
                    fromName,
                    to,
                    toName,
                    text,
                    System.currentTimeMillis(),
                    false,
                    avatar
            );
        }

        private String getUserName(String account) {
            UserInfo user = userConnections.get(account);
            return user != null ? user.getUsername() : account;
        }

        private void storeMessage(Message message) {
            messageStore.add(message);
            if (messageStore.size() > 1000) {
                messageStore.remove(0);
            }
        }

        private void storeNotification(String toAccount, Notification notification) {
            // 获取接收者的通知Map，如果不存在则创建
            Map<String, Notification> userNotificationsMap = userNotifications.computeIfAbsent(
                    toAccount, 
                    k -> new ConcurrentHashMap<>()
            );
            
            // 存储或更新通知（保留最新的消息内容）
            userNotificationsMap.put(notification.getFrom(), notification);
        }

        private void deliverMessage(Message message) {
            UserInfo receiver = userConnections.get(message.getTo());
            if (receiver != null) {
                JSONObject jsonMessage = convertMessageToJson(message);
                sendMessage(jsonMessage.toString(), receiver.getCtx());
                message.setDelivered(true);
                log.debug("消息[{}]已送达用户[{}]", message.getId(), message.getTo());
            } else {
                storeOfflineMessage(message);
                log.info("用户[{}]不在线，消息已存储为离线消息", message.getTo());
            }
        }

        private void storeOfflineMessage(Message message) {
            offlineMessages.computeIfAbsent(message.getTo(), k -> new CopyOnWriteArrayList<>())
                    .add(message);
            if (offlineMessages.get(message.getTo()).size() > MAX_OFFLINE_MESSAGES) {
                offlineMessages.get(message.getTo()).remove(0);
            }
        }

        private void deliverOfflineMessages(String account) {
            List<Message> messages = offlineMessages.remove(account);
            if (messages != null && !messages.isEmpty()) {
                UserInfo user = userConnections.get(account);
                if (user != null) {
                    messages.forEach(msg -> {
                        msg.setDelivered(true);
                        sendMessage(convertMessageToJson(msg).toString(), user.getCtx());
                    });
                    log.info("向用户[{}]发送了{}条离线消息", account, messages.size());
                }
            }
            
            // 新增：发送离线通知给上线用户
            Map<String, Notification> notifications = userNotifications.remove(account);
            if (notifications != null && !notifications.isEmpty()) {
                UserInfo user = userConnections.get(account);
                if (user != null) {
                    // 构建包含所有离线通知的响应
                    JSONObject response = new JSONObject();
                    response.put("type", "notifications");
                    response.put("notifications", convertNotificationsToJson(new ArrayList<>(notifications.values())));
                    sendMessage(response.toString(), user.getCtx());
                    log.info("向用户[{}]发送了{}条离线通知", account, notifications.size());
                }
            }
        }

        private void sendNotificationAlert(String toAccount, Notification notification) {
            UserInfo user = userConnections.get(toAccount);
            if (user != null) {
                JSONObject alert = new JSONObject();
                alert.put("type", "newNotification");
                alert.put("notification", convertNotificationToJson(notification));
                // 确保消息列表更新 - 新增message对象，包含所有必要信息
                alert.put("message", convertMessageToJson(createMessage(
                        notification.getFrom(), 
                        toAccount, 
                        notification.getText(),
                        "" // 添加空的avatar参数，因为通知中没有头像信息
                )));
                sendMessage(alert.toString(), user.getCtx());
                log.info("向用户[{}]发送通知提醒", toAccount);
            }
        }

        private void handleHistoryRequest(ChannelHandlerContext ctx, JSONObject obj) {
            String account = obj.getStr("account");
            String withAccount = obj.getStr("withAccount");
            int limit = obj.getInt("limit", MAX_HISTORY_MESSAGES);

            if (account == null || withAccount == null) {
                sendErrorResponse(ctx, "Both account and withAccount are required");
                return;
            }

            List<Message> history = getHistoryMessages(account, withAccount, limit);
            JSONObject response = new JSONObject();
            response.put("type", "history");
            response.put("messages", convertMessagesToJson(history));

            sendMessage(response.toString(), ctx);
        }

        private void handleNotificationsRequest(ChannelHandlerContext ctx, String account) {
            Map<String, Notification> userNotificationsMap = userNotifications.getOrDefault(account, new ConcurrentHashMap<>());
            
            // 转换为列表格式发送给前端
            List<Notification> notifications = new ArrayList<>(userNotificationsMap.values());
            // 按时间戳排序，最新的在前
            notifications.sort(Comparator.comparingLong(Notification::getTimestamp).reversed());
            
            JSONObject response = new JSONObject();
            response.put("type", "notifications");
            response.put("notifications", convertNotificationsToJson(notifications));
            sendMessage(response.toString(), ctx);
        }

        private void handleMarkNotificationRead(JSONObject obj, String account) {
            // 简化：移除通知，因为我们不再需要跟踪已读状态
            String fromAccount = obj.getStr("from");
            if (fromAccount != null) {
                Map<String, Notification> userNotificationsMap = userNotifications.get(account);
                if (userNotificationsMap != null) {
                    userNotificationsMap.remove(fromAccount);
                }
            }
        }

        private List<Message> getHistoryMessages(String account1, String account2, int limit) {
            return messageStore.stream()
                    .filter(msg -> (msg.getFrom().equals(account1) && msg.getTo().equals(account2)) ||
                            (msg.getFrom().equals(account2) && msg.getTo().equals(account1)))
                    .sorted(Comparator.comparingLong(Message::getTimestamp).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        private JSONObject convertMessageToJson(Message message) {
            JSONObject json = new JSONObject();
            json.put("id", message.getId());
            json.put("from", message.getFrom());
            json.put("fromName", message.getFromName());
            json.put("to", message.getTo());
            json.put("toName", message.getToName());
            json.put("text", message.getText());
            json.put("timestamp", message.getTimestamp());
            json.put("delivered", message.isDelivered());
            json.put("avatar", message.getAvatar()); // 添加头像字段
            return json;
        }

        private JSONArray convertMessagesToJson(List<Message> messages) {
            JSONArray array = new JSONArray();
            messages.forEach(msg -> array.add(convertMessageToJson(msg)));
            return array;
        }

        private JSONObject convertNotificationToJson(Notification notification) {
            JSONObject json = new JSONObject();
            json.put("id", notification.getId());
            json.put("from", notification.getFrom());
            json.put("fromName", notification.getFromName());
            json.put("text", notification.getText());
            json.put("timestamp", notification.getTimestamp());
            return json;
        }

        private JSONArray convertNotificationsToJson(List<Notification> notifications) {
            JSONArray array = new JSONArray();
            notifications.forEach(n -> array.add(convertNotificationToJson(n)));
            return array;
        }

        private void sendOnlineUsersList() {
            JSONObject response = new JSONObject();
            response.put("type", "onlineUsers");

            JSONArray users = new JSONArray();
            userConnections.forEach((account, userInfo) -> {
                JSONObject user = new JSONObject();
                user.put("account", account);
                user.put("name", userInfo.getUsername());
                users.add(user);
            });
            response.put("users", users);

            broadcastMessage(response.toString());
        }

        private void sendErrorResponse(ChannelHandlerContext ctx, String error) {
            JSONObject response = new JSONObject();
            response.put("type", "error");
            response.put("message", error);
            sendMessage(response.toString(), ctx);
        }

        private void sendSuccessResponse(ChannelHandlerContext ctx, String type, String message) {
            JSONObject response = new JSONObject();
            response.put("type", type + "Response");
            response.put("status", "success");
            response.put("message", message);
            sendMessage(response.toString(), ctx);
        }

        private String generateMessageId(String account) {
            return account + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            String disconnectedUser = null;
            for (Map.Entry<String, UserInfo> entry : userConnections.entrySet()) {
                if (entry.getValue().getCtx().equals(ctx)) {
                    disconnectedUser = entry.getKey();
                    break;
                }
            }

            if (disconnectedUser != null) {
                userConnections.remove(disconnectedUser);
                log.info("用户[{}]断开连接，当前在线用户数: {}", disconnectedUser, userConnections.size());
                sendOnlineUsersList();
            }

            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("WebSocket处理异常", cause);
            ctx.close();
        }
    }

    private void sendMessage(String message, ChannelHandlerContext ctx) {
        try {
            if (ctx != null && ctx.channel().isActive()) {
                ctx.writeAndFlush(new TextWebSocketFrame(message));
            }
        } catch (Exception e) {
            log.error("发送消息失败", e);
        }
    }

    private void broadcastMessage(String message) {
        userConnections.values().forEach(user -> sendMessage(message, user.getCtx()));
    }
}