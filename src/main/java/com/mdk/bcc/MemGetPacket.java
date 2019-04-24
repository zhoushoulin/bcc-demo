package com.mdk.bcc;

public class MemGetPacket extends BccPacket {
    @Override
    String getCommand() {
        return Command.MEM_GET;
    }
}
