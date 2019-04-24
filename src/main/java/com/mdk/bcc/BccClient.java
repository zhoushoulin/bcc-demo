package com.mdk.bcc;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


public class BccClient {
    private static final String BCC_HOST = "192.168.100.41";
    private static final int BCC_PORT = 4001;
    public static final Queue<String> requestQueue = new ConcurrentLinkedQueue<String>();

    public static void main(String[] args) {
        new BccClient().start();
    }

    private void start() {
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new BccMessageCodec());
                }
            });

            ChannelFuture f = b.connect(BCC_HOST, BCC_PORT).sync();

            System.out.println("connected to " + BCC_HOST + ":" + BCC_PORT);

            f.channel().closeFuture().sync();

            System.out.println("client close");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }
}
