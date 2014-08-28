package com.nashsoftware.ardrone2.video;

import java.awt.image.BufferedImage;

public interface DroneVideoListener
{
    void frameReceived(BufferedImage img);
}
