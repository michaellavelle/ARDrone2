
package com.nashsoftware.ardrone2;

/*
The MIT License (MIT)

        Copyright (c) 2013 Nashsoftware, Josh Long <longjos@gmail.com>

        Permission is hereby granted, free of charge, to any person obtaining a copy of
        this software and associated documentation files (the "Software"), to deal in
        the Software without restriction, including without limitation the rights to
        use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
        the Software, and to permit persons to whom the Software is furnished to do so,
        subject to the following conditions:

        The above copyright notice and this permission notice shall be included in all
        copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
        FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
        COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
        IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
        CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE
*/

import com.codeminders.ardrone.*;
import com.nashsoftware.ardrone2.video.DroneVideoListener;
import com.nashsoftware.ardrone2.video.VideoReader;
import org.apache.log4j.Logger;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

public class ARDrone2 extends ARDrone {


    private Logger log = Logger.getLogger(getClass().getName());

    protected static final int CMD_QUEUE_SIZE = 64;
    protected State state = State.DISCONNECTED;
    protected Object state_mutex = new Object();

    protected static final int NAVDATA_PORT = 5554;
    protected static final int VIDEO_PORT = 5555;

    protected static byte[] DEFAULT_DRONE_IP = {(byte) 192, (byte) 168, (byte) 1, (byte) 1};

    protected InetAddress drone_addr;
    protected DatagramSocket cmd_socket;

    protected CommandQueue cmd_queue = new CommandQueue(CMD_QUEUE_SIZE);

    protected NavDataReader nav_data_reader;
    protected VideoReader video_reader;
    protected CommandSender cmd_sender;

    protected Thread nav_data_reader_thread;
    protected Thread cmd_sending_thread;
    protected Thread video_reader_thread;

    protected List<DroneStatusChangeListener> status_listeners = new LinkedList<DroneStatusChangeListener>();
    protected List<DroneVideoListener> image_listeners = new LinkedList<DroneVideoListener>();


    public ARDrone2() throws UnknownHostException {
        super(InetAddress.getByAddress(DEFAULT_DRONE_IP));
    }

    public ARDrone2(InetAddress drone_addr) {
        super(drone_addr);
    }


    /**
     * Initiate drone connection procedure.
     *
     * @throws IOException
     */
    @Override
    public void connect() throws IOException {
        try {
            cmd_socket = new DatagramSocket();
            // control_socket = new Socket(drone_addr, CONTROL_PORT);

            cmd_sender = new CommandSender(cmd_queue, this, drone_addr, cmd_socket);
            cmd_sending_thread = new Thread(cmd_sender);
            cmd_sending_thread.start();

            nav_data_reader = new NavDataReader(this, drone_addr, NAVDATA_PORT);
            nav_data_reader_thread = new Thread(nav_data_reader);
            nav_data_reader_thread.start();

            video_reader = new com.nashsoftware.ardrone2.video.VideoReader(this, drone_addr, VIDEO_PORT);
            video_reader_thread = new Thread(video_reader);
            video_reader_thread.start();

            changeState(State.CONNECTING);

        } catch (IOException ex) {
            changeToErrorState(ex);
            throw ex;
        }
    }

    protected void changeState(State newstate) throws IOException {
        if (newstate == State.ERROR)
            changeToErrorState(null);

        synchronized (state_mutex) {
            if (state != newstate) {
                log.debug("State changed from " + state + " to " + newstate);
                state = newstate;

                // We automatically switch to DEMO from bootstrap
                if (state == State.BOOTSTRAP)
                    sendDemoNavigationData();

                state_mutex.notifyAll();
            }
        }

        if (newstate == State.DEMO) {
            synchronized (status_listeners) {
                for (DroneStatusChangeListener l : status_listeners)
                    l.ready();
            }
        }
    }

    public void imageFrameReceived(BufferedImage img) {
        synchronized (image_listeners) {
            for (DroneVideoListener l : image_listeners)
                l.frameReceived(img);
        }
    }

}
