import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.internal.StringUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

@ChannelHandler.Sharable
public class HttpImgServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) {
        if (!fullHttpRequest.decoderResult().isSuccess()) {
            sendError(channelHandlerContext, HttpResponseStatus.FORBIDDEN);
            return;
        }

        if (fullHttpRequest.method() != HttpMethod.GET) {
            sendError(channelHandlerContext, HttpResponseStatus.FORBIDDEN);
            return;
        }

        HttpHeaders httpHeaders = fullHttpRequest.headers();
        String userAgent = httpHeaders.get(HttpHeaderNames.USER_AGENT);

        if (StringUtil.isNullOrEmpty(userAgent)) {
            sendError(channelHandlerContext, HttpResponseStatus.FORBIDDEN);
            return;
        }

        String referer = httpHeaders.get(HttpHeaderNames.REFERER);
        boolean flag = false;

        if (HttpImgServer.domainList == null || HttpImgServer.domainList.size() == 0) {
            flag = true;
        } else if (!StringUtil.isNullOrEmpty(referer)) {
            for (String domain : HttpImgServer.domainList) {
                if (referer.contains(domain)) {
                    flag = true;
                    break;
                }
            }
        }

        if (!flag) {
            sendError(channelHandlerContext, HttpResponseStatus.FORBIDDEN);
            return;
        }

        File file = HttpImgServer.fileList.get(HttpImgServer.random.nextInt(HttpImgServer.fileList.size()));
        RandomAccessFile randomAccessFile = null;
        long fileLength;

        try {
            randomAccessFile = new RandomAccessFile(file, "r");
            fileLength = randomAccessFile.length();
        } catch (Exception e) {
            e.printStackTrace();

            try {
                if (randomAccessFile != null) {
                    randomAccessFile.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            sendError(channelHandlerContext, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            return;
        }

        DefaultHttpResponse defaultHttpResponse = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
        defaultHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/png");
        HttpUtil.setContentLength(defaultHttpResponse, fileLength);
        channelHandlerContext.write(defaultHttpResponse);
        channelHandlerContext.write(new DefaultFileRegion(randomAccessFile.getChannel(), 0, fileLength));

        RandomAccessFile finalRandomAccessFile = randomAccessFile;

        channelHandlerContext.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                .addListener((ChannelFutureListener) channelFuture -> {
                    if (!channelFuture.isSuccess()) {
                        channelHandlerContext.close();
                    }
                    finalRandomAccessFile.close();
                });
    }

    private void sendError(ChannelHandlerContext channelHandlerContext, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status);
        HttpUtil.setContentLength(response, 0);
        ChannelFuture channelFuture = channelHandlerContext.writeAndFlush(response);
        channelFuture.addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!(cause instanceof ReadTimeoutException)) {
            cause.printStackTrace();
        }
        ctx.close();
    }
}


