import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpImgServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private Random random = new Random();

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
        String referer = httpHeaders.get(HttpHeaderNames.REFERER);
        boolean flag = false;

        if (HttpImgServer.domainList == null || HttpImgServer.domainList.size() == 0) {
            flag = true;
        } else if (referer != null) {
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

        File file = HttpImgServer.fileList.get(random.nextInt(HttpImgServer.fileList.size()));
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
        defaultHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpImgServer.mimeTypesMap.getContentType(file));
        HttpUtil.setContentLength(defaultHttpResponse, fileLength);
        channelHandlerContext.write(defaultHttpResponse);
        channelHandlerContext.write(new DefaultFileRegion(randomAccessFile.getChannel(), 0, fileLength));
        channelHandlerContext.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
    }

    private void sendError(ChannelHandlerContext channelHandlerContext, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status);
        HttpUtil.setContentLength(response, 0);
        channelHandlerContext.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }
}


