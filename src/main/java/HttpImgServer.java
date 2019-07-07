import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import org.apache.commons.io.IOUtils;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class HttpImgServer {

    static List<File> fileList = new ArrayList<>();
    static MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
    static List<String> domainList;

    public static void main(String[] args) {
        InputStream inputStream = null;
        ServerConfigEntity serverConfigEntity;

        try {
            inputStream = HttpImgServer.class.getResourceAsStream("/server-config.json");
            serverConfigEntity = JSONObject.parseObject(IOUtils.toString(inputStream, CharsetUtil.UTF_8), ServerConfigEntity.class);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        domainList = serverConfigEntity.getDomainList();
        setImgFileList(new File(serverConfigEntity.getPath()));

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();

            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.WARN))
                    .childHandler(new HttpImgServerInitializer());

            Channel channel = serverBootstrap.bind(serverConfigEntity.getPort()).sync().channel();
            channel.closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private static void setImgFileList(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();

            if (files == null) {
                return;
            }

            for (File fileTemp : files) {
                setImgFileList(fileTemp);
            }
        } else if (file.isFile()) {
            if (mimeTypesMap.getContentType(file).contains("image")) {
                fileList.add(file);
            }
        }
    }
}
