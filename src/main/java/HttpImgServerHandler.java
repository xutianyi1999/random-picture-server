import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
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

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws MalformedURLException {
        if (!fullHttpRequest.decoderResult().isSuccess()) {
            ctx.close();
            return;
        }

        if (fullHttpRequest.method() != HttpMethod.GET) {
            ctx.close();
            return;
        }

        HttpHeaders httpHeaders = fullHttpRequest.headers();
        String userAgent = httpHeaders.get(HttpHeaderNames.USER_AGENT);

        if (StringUtil.isNullOrEmpty(userAgent)) {
            ctx.close();
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
            ctx.close();
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

            ctx.close();
            return;
        }

        DefaultHttpResponse defaultHttpResponse = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
        defaultHttpResponse.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "image/png")
                .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                .set(HttpHeaderNames.CONTENT_LENGTH, fileLength);

        ctx.write(defaultHttpResponse);
        ctx.write(new DefaultFileRegion(randomAccessFile.getChannel(), 0, fileLength));

        RandomAccessFile finalRandomAccessFile = randomAccessFile;
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                .addListener(channelFuture -> {
                    if (!channelFuture.isSuccess()) {
                        ctx.close();
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


