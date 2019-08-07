import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HttpImgServer {

    static List<File> fileList = new ArrayList<>();
    static List<String> domainList;
    final static Random random = new Random();
    private final static String os = System.getProperty("os.name");
    private final static boolean isLinux = os.contains("Linux");
    private final static boolean isWindows = os.contains("Win");
    private final static MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();

    public static void main(String[] args) {
        ServerConfigEntity serverConfigEntity;

        try (
                InputStream inputStream = HttpImgServer.class.getResourceAsStream("/server-config.json")
        ) {
            serverConfigEntity = JSONObject.parseObject(inputStream, ServerConfigEntity.class);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        domainList = serverConfigEntity.getDomainList();
        setImgFileList(new File(serverConfigEntity.getPath()));

        if (fileList.size() == 0) {
            System.out.println("ERROR: Picture Not Found");
            return;
        }

        EventLoopGroup bossGroup;
        EventLoopGroup workerGroup;
        Class<? extends ServerSocketChannel> serverSocketChannel;

        if (isLinux) {
            bossGroup = new EpollEventLoopGroup();
            workerGroup = new EpollEventLoopGroup();
            serverSocketChannel = EpollServerSocketChannel.class;
        } else {
            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();
            serverSocketChannel = NioServerSocketChannel.class;
        }

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();

            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(serverSocketChannel)
                    .handler(new LoggingHandler(LogLevel.ERROR))
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
            if (isWindows) {
                if (mimeTypesMap.getContentType(file).contains("image")) {
                    fileList.add(file);
                }
            } else {
                fileList.add(file);
            }
        }
    }
}
