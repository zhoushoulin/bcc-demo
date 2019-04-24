package com.mdk.bcc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.util.CharsetUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BccMessageCodec extends ByteToMessageCodec<BccPacket> {
    private static final int soh = 0x01;
    private static final int stx = 0x02;
    private static final int etx = 0x03;
    private static final int etb = 0x17;
    private static final int eot = 0x04;
    private static final int ack = 0x06;
    private static final int nak = 0x15;

    // <SOH>00031<STX>1/1/1>ADMIN:ALIVE<ETX>00061<EOT>

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        AdminAlivePacket adminAlivePacket = new AdminAlivePacket();
        BccClient.requestQueue.add(Command.ADMIN_ALIVE);
        ctx.channel().writeAndFlush(adminAlivePacket);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, BccPacket msg, ByteBuf out) throws Exception {
        ByteBuf data = ByteBufAllocator.DEFAULT.buffer();
        data.writeByte(soh);
        int length = 1 + 5 + 1 + msg.bedId.length() + msg.getCommand().length() + 1 + 5 + 1;
        data.writeCharSequence(length2Chars(length), CharsetUtil.US_ASCII);

        data.writeByte(stx);
        data.writeCharSequence(msg.bedId, CharsetUtil.US_ASCII);
        data.writeCharSequence(msg.getCommand(), CharsetUtil.US_ASCII);
        if (msg.isEtx) {
            data.writeByte(etx);
        } else {
            data.writeByte(etb);
        }
        int checkSum = calcCheckSum(data);
        data.writeCharSequence(length2Chars(checkSum), CharsetUtil.US_ASCII);
        data.writeByte(eot);

        for (int i = 0; i < data.readableBytes(); i++) {
            int c = data.getByte(i) & 0xff;
            if (c == 'e') {
                out.writeByte('e');
                out.writeByte('e');
            } else if (c == 'E') {
                out.writeByte('E');
                out.writeByte('E');
            } else if (c == 'd') {
                out.writeByte('e');
                out.writeByte('x');
            } else if (c == 'D') {
                out.writeByte('E');
                out.writeByte('X');
            } else {
                out.writeByte(c);
            }
        }

        System.out.println("<<<发送:" + out.toString(CharsetUtil.US_ASCII));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        ByteBuf data = ByteBufAllocator.DEFAULT.buffer();
        for (int i = 0; i < in.readableBytes(); i++) {
            int cur = in.getByte(i) & 0xff;

            if (i < in.readableBytes() - 1) {
                int next = in.getByte(i + 1);
                if (cur == 'E' && next == 'E') {
                    data.writeByte('E');
                    i++;
                } else if (cur == 'E' && next == 'X') {
                    data.writeByte('D');
                    i++;
                } else if (cur == 'e' && next == 'e') {
                    data.writeByte('e');
                    i++;
                } else if (cur == 'e' && next == 'x') {
                    data.writeByte('d');
                    i++;
                } else {
                    data.writeByte(cur);
                }
            } else {
                data.writeByte(cur);
            }
        }


        String cmd = BccClient.requestQueue.peek();
        if (cmd == null) {
            return;
        }

        System.out.println(">>>接收：" + ByteBufUtil.prettyHexDump(data));
        System.out.println(">>> cmd is " + cmd);
        String str = data.toString(CharsetUtil.UTF_8);

        if (cmd.equals(Command.ADMIN_ALIVE)) {
            if (str.contains("GNACK")) {
                Pattern pattern = Pattern.compile("^(.*)/1/1>(.*)");
                Matcher matcher = pattern.matcher(str.substring(7));
                if (matcher.matches() && matcher.groupCount() >= 2) {
                    String bedId = matcher.group(1);
                    System.out.println(">>>bedId is " + bedId);

                    BccClient.requestQueue.poll();

                    MemGetPacket memGetPacket = new MemGetPacket();
                    memGetPacket.bedId = bedId + "/1/1>";
                    BccClient.requestQueue.add(Command.MEM_GET);
                    ctx.channel().writeAndFlush(memGetPacket);
                }
            }
        } else if (cmd.equals(Command.MEM_GET)) {
            if (str.contains("GNACK")) {
                int last = str.charAt(str.length() - 1) & 0xff;
                int etxOrEtb = str.charAt(str.length() - 7) & 0xff;

                if (last == eot && etxOrEtb == etb) {
                    ctx.executor().schedule(new Runnable() {
                        @Override
                        public void run() {
                            ctx.channel().writeAndFlush(ack);
                            System.out.println("22222222222222222 write ack");
                        }
                    }, 100, TimeUnit.MILLISECONDS);
                } else if (last == eot && etxOrEtb == etx) {
                    BccClient.requestQueue.poll();
                    System.out.println("11111111111111111111");
                }
            }
        }
    }

    private String length2Chars(int length) {
        StringBuilder sb = new StringBuilder();
        do {
            sb.append(length % 10);
            length = length / 10;
        } while (length % 10 > 0);

        if (sb.length() < 5) {
            int len = 5 - sb.length();
            while (len > 0) {
                sb.append("0");
                len--;
            }
        }

        return sb.reverse().toString();
    }

    private int calcCheckSum(ByteBuf buf) {
        int sum = 0;
        for (int i = 0; i < buf.readableBytes(); i++) {
            sum += buf.getByte(i);
        }
        sum = sum % 256;
        return sum;
    }
}
