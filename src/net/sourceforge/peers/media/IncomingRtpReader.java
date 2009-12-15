/*
    This file is part of Peers, a java SIP softphone.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
    
    Copyright 2007, 2008, 2009 Yohann Martineau 
*/

package net.sourceforge.peers.media;

import gov.nist.jrtp.RtpErrorEvent;
import gov.nist.jrtp.RtpException;
import gov.nist.jrtp.RtpListener;
import gov.nist.jrtp.RtpPacket;
import gov.nist.jrtp.RtpPacketEvent;
import gov.nist.jrtp.RtpSession;
import gov.nist.jrtp.RtpStatusEvent;
import gov.nist.jrtp.RtpTimeoutEvent;

import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import net.sourceforge.peers.Logger;

public class IncomingRtpReader implements RtpListener {

    private RtpSession rtpSession;
    private SourceDataLine line;

    public IncomingRtpReader(RtpSession rtpSession) throws IOException { 
        super();
        this.rtpSession = rtpSession;
        rtpSession.addRtpListener(this);
    }
    
    public void start() throws IOException, RtpException {
        AudioFormat format = new AudioFormat((float)8000, 16, 1, true, false);
        
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try {
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
        } catch (LineUnavailableException ex) {
            Logger.error("line unavailable", ex);
            return;
        }
        line.start();
        
        rtpSession.receiveRTPPackets();
    }
    
    public synchronized void stop() {
        if (line != null) {
            try {
                rtpSession.stopRtpPacketReceiver();
            } catch (Exception e) {
                Logger.error("exception in rtpSession.stopRtpPacketReceiver()",
                        e);
            }
            try {
                rtpSession.shutDown();
            } catch (Exception e) {
                Logger.error("exception in rtpSession.shutDown()", e);
            }
            
            line.drain();
            line.stop();
            line.close();
            line = null;
        }
    }

    public void handleRtpErrorEvent(RtpErrorEvent rtpErrorEvent) {
        Logger.debug("IncomingRtpReader.handleRtpErrorEvent() "
                + rtpErrorEvent);
    }

    public void handleRtpPacketEvent(RtpPacketEvent rtpEvent) {
        RtpPacket rtpPacket = rtpEvent.getRtpPacket();

        byte[] data = new byte[rtpPacket.getData().length - 12];
        //remove RTP header: rtpPacket.getData() returns raw
        //packet bytes (with headers)
        System.arraycopy(rtpPacket.getData(), 12, data, 0,
                rtpPacket.getData().length - 12);

		byte[] rawBuf = new byte[data.length * 2];
		for (int i = 0; i < data.length; ++i) {
			short decoded = AudioUlawEncodeDecode02.decode(data[i]);
			rawBuf[2 * i] = (byte)(decoded & 0xFF);
			rawBuf[2 * i + 1] = (byte)(decoded >>> 8);
		}
		line.write(rawBuf, 0, rawBuf.length);
        
    }

    public void handleRtpStatusEvent(RtpStatusEvent rtpStatusEvent) {
        Logger.debug("IncomingRtpReader.handleRtpStatusEvent() "
                + rtpStatusEvent);
    }

    public void handleRtpTimeoutEvent(RtpTimeoutEvent rtpTimeoutEvent) {
        Logger.debug("IncomingRtpReader.handleRtpTimeoutEvent() "
                + rtpTimeoutEvent);
    }
}
