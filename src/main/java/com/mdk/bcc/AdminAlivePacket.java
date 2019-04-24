package com.mdk.bcc;

public class AdminAlivePacket extends BccPacket {
    public AdminAlivePacket() {
        bedId = "1/1/1>";
    }

    @Override
    String getCommand() {
        return Command.ADMIN_ALIVE;
    }
}
