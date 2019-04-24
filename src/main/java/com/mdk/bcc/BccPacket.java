package com.mdk.bcc;

public abstract class BccPacket {
    public String bedId;

    public boolean isEtx = true; // is end of text

    abstract String getCommand();

//    <soh>length<stx>bedId>information<etx>check<eot>
}
