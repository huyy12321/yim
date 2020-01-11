package com.hyy.yim.handler;

import com.hyy.yim.service.ChatServer;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedNioFile;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URL;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.rtsp.RtspResponseStatuses.CONTINUE;

/**
 * @author hyy
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
   private final String wsUri;
   private static final File INDEX;

   static {
       URL location = HttpRequestHandler.class.getProtectionDomain().getCodeSource().getLocation();
        try{
            String path = location.toURI() + "index.html";
            path = !path.contains("file:") ? path : path.substring(5);
            INDEX = new File(path);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to locate index.html",e);
        }
   }

    public HttpRequestHandler(String wsUri) {
        this.wsUri = wsUri;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.uri().contains(wsUri)) {
            String uri = request.uri();
            int i = uri.indexOf("id=");
            if(i == -1) {
                ctx.write("id没传");
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                return;
            }
            String id = uri.substring(i + 3);
            Channel channel = ChatServer.userMap.get(id);
            if(channel != null) {
                ctx.writeAndFlush("id已存在");
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            } else{
                ChatServer.userMap.put(id,ctx.channel());
                // 增加引用计数，传递给下一个handler
                // 之所以需要调用 retain()方法，是因为调用 channelRead()方法完成之后，它将调用 FullHttpRequest 对象上的 release()方法以释放它的资源。
                ctx.fireChannelRead(request.retain());
            }
        } else {
            if(HttpHeaderUtil.is100ContinueExpected(request)) {
                // 为了符合http 1.1规范
                ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
            }
            RandomAccessFile file = new RandomAccessFile(INDEX, "r");
            HttpResponse response = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
            boolean keepAlive = HttpHeaderUtil.isKeepAlive(request);
            if (keepAlive) {
                response.headers().setLong(HttpHeaderNames.CONTENT_LENGTH,file.length());
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            ctx.write(response);
            if (ctx.pipeline().get(SslHandler.class) == null) {
                // 如果没有使用加密和压缩那么可以通过将 index.html 的内容存储到 DefaultFileRegion 中来达到最佳效率。这将会利用零拷贝特性来进行内容的传输
                ctx.write(new DefaultFileRegion(file.getChannel(), 0, file.length()));
            } else {
                ctx.write(new ChunkedNioFile(file.getChannel()));
            }
            // HttpRequestHandler 将写一个 LastHttpContent 来标记响应的结束
            ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if (!keepAlive) {
                // 如果没有请求 keep-alive ，那么 HttpRequestHandler 将会添加一个 ChannelFutureListener到最后一次写出动作的 ChannelFuture，并关闭该连接
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }
    }
}
