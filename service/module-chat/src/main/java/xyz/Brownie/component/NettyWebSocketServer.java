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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class NettyWebSocketServer {

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    // 存储用户连接
    public static final Map<String, ChannelHandlerContext> userConnections = new ConcurrentHashMap<>();

    // 存储所有消息记录
    private static final List<JSONObject> messageStore = new CopyOnWriteArrayList<>();

    // 存储离线消息
    private static final Map<String, List<JSONObject>> offlineMessages = new ConcurrentHashMap<>();

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
                        pipeline.addLast(new HttpObjectAggregator(65536));
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
            JSONObject obj = JSONUtil.parseObj(message);
            String username = obj.getStr("username");

            // 处理连接注册
            if ("connect".equals(obj.getStr("type"))) {
                userConnections.put(username, ctx);
                sendOnlineUsers();
                sendOfflineMessages(username);
                return;
            }

            log.info("服务端收到用户username={}的消息:{}", username, message);

            // 处理不同类型的消息
            if (obj.containsKey("type")) {
                handleSpecialMessage(obj, username, ctx);
                return;
            }

            String toUsername = obj.getStr("to");
            String text = obj.getStr("text");

            // 添加消息ID和时间戳
            obj.put("from", username);
            obj.put("timestamp", System.currentTimeMillis());
            obj.put("id", generateMessageId(username));

            // 存储消息
            messageStore.add(obj);

            // 尝试发送给目标用户
            ChannelHandlerContext toCtx = userConnections.get(toUsername);
            if (toCtx != null) {
                sendMessage(obj.toString(), toCtx);
                log.info("发送给用户username={}，消息：{}", toUsername, obj.toString());
            } else {
                // 用户不在线，存储为离线消息
                storeOfflineMessage(toUsername, obj);
                log.info("用户username={}不在线，消息已存储为离线消息", toUsername);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // 移除断开连接的客户端
            userConnections.entrySet().removeIf(entry -> entry.getValue().equals(ctx));
            sendOnlineUsers();
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("发生错误", cause);
            ctx.close();
        }
    }

    private void handleSpecialMessage(JSONObject message, String username, ChannelHandlerContext ctx) {
        String type = message.getStr("type");

        if ("getHistory".equals(type)) {
            // 处理历史消息请求
            String withUser = message.getStr("withUser");
            List<JSONObject> history = getHistoryMessages(username, withUser);

            JSONObject response = new JSONObject();
            response.put("type", "history");
            response.put("messages", history);

            sendMessage(response.toString(), ctx);
        }
    }

    private List<JSONObject> getHistoryMessages(String user1, String user2) {
        List<JSONObject> result = new CopyOnWriteArrayList<>();
        for (JSONObject msg : messageStore) {
            String from = msg.getStr("from");
            String to = msg.getStr("to");
            if ((from.equals(user1) && to.equals(user2)) ||
                    (from.equals(user2) && to.equals(user1))) {
                result.add(msg);
            }
        }
        return result;
    }

    private void sendOnlineUsers() {
        JSONObject result = new JSONObject();
        JSONArray array = new JSONArray();
        result.put("users", array);

        for (String username : userConnections.keySet()) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("username", username);
            array.add(jsonObject);
        }

        sendAllMessage(JSONUtil.toJsonStr(result));
    }

    private void sendOfflineMessages(String username) {
        List<JSONObject> messages = offlineMessages.remove(username);
        if (messages != null && !messages.isEmpty()) {
            ChannelHandlerContext ctx = userConnections.get(username);
            if (ctx != null) {
                for (JSONObject msg : messages) {
                    sendMessage(msg.toString(), ctx);
                }
                log.info("向用户username={}发送了{}条离线消息", username, messages.size());
            }
        }
    }

    private void storeOfflineMessage(String username, JSONObject message) {
        offlineMessages.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>()).add(message);
    }

    private String generateMessageId(String username) {
        return username + "-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }

    private void sendMessage(String message, ChannelHandlerContext ctx) {
        try {
            log.info("服务端给客户端[{}]发送消息{}", ctx.channel().id(), message);
            ctx.writeAndFlush(new TextWebSocketFrame(message));
        } catch (Exception e) {
            log.error("服务端发送消息给客户端失败", e);
        }
    }

    private void sendAllMessage(String message) {
        try {
            for (ChannelHandlerContext ctx : userConnections.values()) {
                log.info("服务端给客户端[{}]发送消息{}", ctx.channel().id(), message);
                ctx.writeAndFlush(new TextWebSocketFrame(message));
            }
        } catch (Exception e) {
            log.error("服务端发送消息给客户端失败", e);
        }
    }
}