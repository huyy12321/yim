package com.hyy.yim.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * @author hyy
 */
public class TextWebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final ChannelGroup group;

    public TextWebSocketFrameHandler(ChannelGroup group) {
        this.group = group;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        // 往group中所有的客户端发消息，增加消息引用计数
        // retain()方法的调用是必需的，因为当 messageReceived()方法返回时，TextWebSocketFrame
        // 的引用计数将会被减少。由于所有的操作都是异步的，因此，writeAndFlush()方法可能会在
        // messageReceived()方法返回之后完成，而且它绝对不能访问一个已经失效的引用。

        group.writeAndFlush(msg.retain());
    }

    /**
     * 重写userEventTriggered 通知所有所有已经连接的WebSocket客户端 新的客户端已经连接上了
     * @param ctx
     * @param evt
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if(evt == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            // 从 pipeline 中移除 HttpRequestHandler,之后将不会接收到http信息
            ctx.pipeline().remove(HttpRequestHandler.class);
            group.writeAndFlush(new TextWebSocketFrame(
                    "Client " + ctx.channel().id() + "joined"));
            // 将新的WebSock channel 加入到group中，以便之后接收消息
            group.add(ctx.channel());
        } else{
            super.userEventTriggered(ctx, evt);
        }
    }
}
