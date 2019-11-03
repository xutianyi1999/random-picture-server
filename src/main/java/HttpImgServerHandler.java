import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.internal.StringUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

@ChannelHandler.Sharable
public class HttpImgServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final static Random RANDOM = new Random();

    private static void sendError(ChannelHandlerContext channelHandlerContext, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status);
        HttpUtil.setContentLength(response, 0);
        channelHandlerContext.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) throws MalformedURLException {
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

        boolean flag = false;

        if (HttpImgServer.domainList == null) {
            flag = true;
        } else {
            String referer = httpHeaders.get(HttpHeaderNames.REFERER);

            if (!StringUtil.isNullOrEmpty(referer) && HttpImgServer.domainList.contains(new URL(referer).getHost())) {
                flag = true;
            }
        }

        if (!flag) {
            sendError(channelHandlerContext, HttpResponseStatus.FORBIDDEN);
            return;
        }

        File file = HttpImgServer.FILE_LIST.get(RANDOM.nextInt(HttpImgServer.FILE_LIST.size()));
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
        defaultHttpResponse.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "image/png")
                .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                .set(HttpHeaderNames.CONTENT_LENGTH, fileLength);

        channelHandlerContext.write(defaultHttpResponse);
        channelHandlerContext.write(new DefaultFileRegion(randomAccessFile.getChannel(), 0, fileLength));

        RandomAccessFile finalRandomAccessFile = randomAccessFile;
        channelHandlerContext.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                .addListener(channelFuture -> {
                    if (!channelFuture.isSuccess()) {
                        channelHandlerContext.close();
                    }
                    finalRandomAccessFile.close();
                });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!(cause instanceof ReadTimeoutException)) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}


