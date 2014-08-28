package com.nashsoftware.ardrone2;

import com.codeminders.ardrone.*;
import com.codeminders.ardrone.data.decoder.ardrone10.navdata.ARDrone10NavData;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;



public class NavDataReader extends DataReader {

    private static final int BUFSIZE = 4096;

    public NavDataReader(ARDrone2 drone, InetAddress drone_addr, int navdata_port) throws IOException
    {
        super(drone, drone_addr, navdata_port, BUFSIZE);
    }

    @Override
    void handleReceivedMessageKey(SelectionKey key, ByteBuffer inbuf) throws Exception
    {

        if(key.isWritable())
        {
            byte[] trigger_bytes = {0x01, 0x00, 0x00, 0x00};
            ByteBuffer trigger_buf = ByteBuffer.allocate(trigger_bytes.length);
            trigger_buf.put(trigger_bytes);
            trigger_buf.flip();
            channel.write(trigger_buf);
            channel.register(selector, SelectionKey.OP_READ);
        } else if(key.isReadable())
        {
            inbuf.clear();
            int len = channel.read(inbuf);
            byte[] packet = new byte[len];
            inbuf.flip();
            inbuf.get(packet, 0, len);

            NavData nd = ARDrone10NavData.createFromData(inbuf,len);

            drone.navDataReceived(nd);
        }
    }

}
