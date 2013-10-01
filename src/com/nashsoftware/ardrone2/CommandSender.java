package com.nashsoftware.ardrone2;

import com.codeminders.ardrone.CommandQueue;
import com.codeminders.ardrone.DroneCommand;
import com.codeminders.ardrone.commands.ATCommand;
import com.codeminders.ardrone.commands.QuitCommand;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;


public class CommandSender implements Runnable
{
    private static final int CMD_PORT = 5556;

    private CommandQueue cmd_queue;
    private ARDrone2 drone;
    private InetAddress drone_addr;
    private DatagramSocket cmd_socket;
    private int              sequence = 1;

    private Logger log      = Logger.getLogger(getClass().getName());

    public CommandSender(CommandQueue cmd_queue, ARDrone2 drone, InetAddress drone_addr, DatagramSocket cmd_socket)
    {
        this.cmd_queue = cmd_queue;
        this.drone = drone;
        this.drone_addr = drone_addr;
        this.cmd_socket = cmd_socket;
    }

    @Override
    public void run()
    {
        while(true)
        {
            try
            {
                DroneCommand c = cmd_queue.take();
                if(c instanceof QuitCommand)
                {
                    // Terminating
                    break;
                }

                if(c instanceof ATCommand)
                {
                    ATCommand cmd = (ATCommand) c;
                    //if(!(c instanceof KeepAliveCommand) && !(c instanceof MoveCommand) && !(c instanceof HoverCommand) && c.getStickyCounter()==0)
                    log.debug("Q[" + cmd_queue.size() + "]Sending AT command " + c);
                    byte[] pdata = cmd.getPacket(sequence++); // TODO: pass
                    // sequence number
                    DatagramPacket p = new DatagramPacket(pdata, pdata.length, drone_addr, CMD_PORT);
                    cmd_socket.send(p);
                }
            } catch(InterruptedException e)
            {
                // ignoring
            } catch(IOException e)
            {
                drone.changeToErrorState(e);
                break;
            }
        }
    }

}