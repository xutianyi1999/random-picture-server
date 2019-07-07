import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.RandomAccessFile;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

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
        String referer = httpHeaders.get(HttpHeaderNames.REFERER);

        if (referer == null || referer.contains("koumakan.club")) {
            sendError(channelHandlerContext, HttpResponseStatus.FORBIDDEN);
            return;
        }

        File file = new File("C:\\Users\\xutia\\Pictures\\v2-18701cc218618f5e315550190ee61dff_hd.jpg");
        RandomAccessFile randomAccessFile;
        long fileLength;

        try {
            randomAccessFile = new RandomAccessFile((file), "r");
            fileLength = randomAccessFile.length();
        } catch (Exception e) {
            e.printStackTrace();
            sendError(channelHandlerContext, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            return;
        }

        DefaultHttpResponse defaultHttpResponse = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
        HttpUtil.setContentLength(defaultHttpResponse, fileLength);
        setContentTypeHeader(defaultHttpResponse, file);
        channelHandlerContext.write(defaultHttpResponse);

        channelHandlerContext.write(
                new DefaultFileRegion(randomAccessFile.getChannel(), 0, fileLength),
                channelHandlerContext.newProgressivePromise()
        );
        channelHandlerContext.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
    }

    private void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
    }

    private void sendError(ChannelHandlerContext channelHandlerContext, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1,
                status,
                Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        HttpUtil.setContentLength(response, response.content().readableBytes());
        channelHandlerContext.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }
}


