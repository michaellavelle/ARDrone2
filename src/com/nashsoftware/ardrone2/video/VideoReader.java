/*
  Original work Copyright (c) <2011>, <Shigeo Yoshida>
  Modified work Copyright (c) 2013, Nashsoftware <longjos@gmail.com>

All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
The names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.nashsoftware.ardrone2.video;

import com.nashsoftware.ardrone2.ARDrone2;
import com.xuggle.xuggler.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;

public class VideoReader implements Runnable {

    protected InetAddress inetaddr = null;
    protected Socket socket = null;
    protected ARDrone2 drone = null;
    protected int port = 0;
    protected boolean done = false;

    public VideoReader(ARDrone2 drone, InetAddress inetaddr, int port) {
        this.inetaddr = inetaddr;
        this.drone = drone;
        this.port = port;
    }

    protected boolean connect() {
        try {
            this.socket = new Socket(inetaddr, port);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    protected InputStream getInputStream() {
        try {
            return socket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void stop() {
        this.done = true;
    }

    @Override
    public void run() {
        try {
            connect();
            decode();
        } catch (Exception e) {
            System.out.println("Restarting Video Stream");
            run();
        }
    }

    private void decode() {
        while (!this.done) {

            if (!IVideoResampler.isSupported(IVideoResampler.Feature.FEATURE_COLORSPACECONVERSION))
                throw new RuntimeException("you must install the GPL version of Xuggler (with IVideoResampler support)");

            IContainer container = IContainer.make();

            if (container.open(getInputStream(), null) < 0)
                throw new IllegalArgumentException("Could not open input stream.");

            int numStreams = container.getNumStreams();
            int videoStreamId = -1;
            IStreamCoder videoCoder = null;
            for (int i = 0; i < numStreams; i++) {
                IStream stream = container.getStream(i);
                IStreamCoder coder = stream.getStreamCoder();

                if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) {
                    videoStreamId = i;
                    videoCoder = coder;
                    break;
                }
            }
            if (videoStreamId == -1)
                throw new RuntimeException("Could not find video stream.");

            if (videoCoder.open() < 0)
                throw new RuntimeException("Could not open video decoder for container.");

            IVideoResampler resampler = null;
            if (videoCoder.getPixelType() != IPixelFormat.Type.BGR24) {
                resampler = IVideoResampler.make(videoCoder.getWidth(), videoCoder.getHeight(), IPixelFormat.Type.BGR24, videoCoder.getWidth(), videoCoder.getHeight(), videoCoder.getPixelType());
                if (resampler == null)
                    throw new RuntimeException("Could not create color space resampler.");
            }
            IPacket packet = IPacket.make();
            long firstTimestampInStream = Global.NO_PTS;
            long systemClockStartTime = 0;
            while (container.readNextPacket(packet) >= 0) {
                if (packet.getStreamIndex() == videoStreamId) {
                    IVideoPicture picture = IVideoPicture.make(videoCoder.getPixelType(), videoCoder.getWidth(), videoCoder.getHeight());
                    try {
                        int offset = 0;
                        while (offset < packet.getSize()) {
                            int bytesDecoded = videoCoder.decodeVideo(picture, packet, offset);
                            if (bytesDecoded < 0)
                                throw new RuntimeException("Error decoding video.");
                            offset += bytesDecoded;

                            if (picture.isComplete()) {
                                IVideoPicture newPic = picture;
                                if (resampler != null) {
                                    newPic = IVideoPicture.make(resampler.getOutputPixelFormat(), picture.getWidth(), picture.getHeight());
                                    if (resampler.resample(newPic, picture) < 0)
                                        throw new RuntimeException("Could not resample video.");
                                }
                                if (newPic.getPixelType() != IPixelFormat.Type.BGR24)
                                    throw new RuntimeException("Could not decode video as BGR 24 bit data.");

                                /**
                                 * We could just display the images as quickly as we
                                 * decode them, but it turns out we can decode a lot
                                 * faster than you think.
                                 *
                                 * So instead, the following code does a poor-man's
                                 * version of trying to match up the frame-rate
                                 * requested for each IVideoPicture with the system
                                 * clock time on your computer.
                                 *
                                 * Remember that all Xuggler IAudioSamples and
                                 * IVideoPicture objects always give timestamps in
                                 * Microseconds, relative to the first decoded item. If
                                 * instead you used the packet timestamps, they can be
                                 * in different units depending on your IContainer, and
                                 * IStream and things can get hairy quickly.
                                 */
                                if (firstTimestampInStream == Global.NO_PTS) {
                                    firstTimestampInStream = picture.getTimeStamp();
                                    systemClockStartTime = System.currentTimeMillis();
                                } else {
                                    long systemClockCurrentTime = System.currentTimeMillis();
                                    long millisecondsClockTimeSinceStartOfVideo = systemClockCurrentTime - systemClockStartTime;
                                    long millisecondsStreamTimeSinceStartOfVideo = (picture.getTimeStamp() - firstTimestampInStream) / 1000;
                                    final long millisecondsTolerance = 50;
                                    final long millisecondsToSleep = (millisecondsStreamTimeSinceStartOfVideo - (millisecondsClockTimeSinceStartOfVideo + millisecondsTolerance));
                                    if (millisecondsToSleep > 0) {
                                        try {
                                            Thread.sleep(millisecondsToSleep);
                                        } catch (InterruptedException e) {
                                            return;
                                        }
                                    }
                                }

                                BufferedImage javaImage = Utils.videoPictureToImage(newPic);
                                drone.imageFrameReceived(javaImage);
                            }
                        }
                    } catch (Exception exc) {
                        exc.printStackTrace();
                    }
                }
            }
        }
    }
}