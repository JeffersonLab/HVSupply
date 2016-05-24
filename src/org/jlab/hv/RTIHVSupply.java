package org.jlab.hv;

/**
 * Created by john on 11/20/15.
 *
 */

import gnu.io.*;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RTIHVSupply {

    NRSerialPort m_SerialPort;
	BufferedReader inBuf;

	public static boolean DEBUG = false;

    public RTIHVSupply(String strPort, int baud) throws IOException {

		m_SerialPort = new NRSerialPort(strPort, 19200);

		m_SerialPort.connect();

        //try {

            if (m_SerialPort.isConnected()) {

				if (DEBUG)
                	Logger.getLogger("global").info("Connected to HV Controller");

				inBuf = new BufferedReader(new InputStreamReader(m_SerialPort.getInputStream()));

				String response = write("?\r");

				handleLogin(response);

			} else {
				// If connect doesn't propagate the NoSuchPortException, throw an IOException here if we failed to connect
				throw new IOException();
			}


        //} catch (IOException e) {
        //    Logger.getLogger("global").warning("Unable to connect to HV Controller");
        //}
    }


	public void disconnect() {
		if (m_SerialPort != null && m_SerialPort.isConnected())
			m_SerialPort.disconnect();
	}


    public static List<String> GetAvailablePorts() {

        List<String> list = new ArrayList<>();

        Enumeration portList = CommPortIdentifier.getPortIdentifiers();

        while (portList.hasMoreElements()) {
            CommPortIdentifier portId = (CommPortIdentifier) portList.nextElement();
            if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                list.add(portId.getName());
            }
        }

        return list;
    }



	private void handleLogin(String response) {
		//System.out.println("Prompt after connect: '"+response+"'");

		if (response.endsWith("->")) {
			// Login required
			try {
				write("l\r");
				write("RTI\r");

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// Ok to continue
	}


	@Deprecated
	String read(int count, int delay) throws IOException {
		StringBuilder strB = new StringBuilder();
		StringBuilder strLog = null;

		if (DEBUG)
			strLog = new StringBuilder();

		//byte[] buf = new byte[count];

		try {

			Thread.sleep(100);

			long timeout = System.currentTimeMillis() + delay;

			while (!inBuf.ready() && System.currentTimeMillis() < timeout)
				Thread.sleep(1);


			//long now = System.currentTimeMillis();
			//boolean isReady = inBuf.ready();

			if (inBuf.ready()) {

				int i;
				for (i = 0; i < count && inBuf.ready(); i++) {
					int c = inBuf.read();
					strB.append((char)c);

					// Debug
					if (strLog != null)
						strLog.append(String.format("%02x ", c));
				}

				// Debug
				if (strLog != null) {
					Logger.getLogger("global").info("Receive: " + strLog.toString());

					if (i < count) {
						Logger.getLogger("global").warning("Read less than expected bytes: " + i + " < " + count);
					}
				}


			} else if (System.currentTimeMillis() >= timeout) {
				Logger.getLogger("global").warning("Serial read timeout");
			}

			//} catch (IOException e) {
			//	//System.out.println(">>EOL");
			//e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return strB.toString();
	}

	@Deprecated
	String read(int delay) {
		StringBuilder strB = new StringBuilder();
		StringBuilder strLog = new StringBuilder();

		byte[] buf = new byte[1024];

		long timeout = System.currentTimeMillis() + delay;

		try {
			Thread.sleep(100);

			//Initial wait
			while (!inBuf.ready() && System.currentTimeMillis() < timeout)
				Thread.sleep(1);

			if (!inBuf.ready())
				return "";

			long now = System.currentTimeMillis();

			while (System.currentTimeMillis() < timeout) {

				if (inBuf.ready()) {

					int c = inBuf.read();
					strB.append((char)c);

					if (DEBUG) {
						strLog.append(String.format("%02x ", c));
						Logger.getLogger("global").info("Receive: " + strLog.toString());
					}

				}
			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return strB.toString();
	}


	/**
	 * Returns a map of channels to their voltages
	 * @return Channel-Voltage map, can be empty if there was a problem communicating with the controller
	 */
	public Map<Integer, Float> getVoltages() {
		String strOut = requestReadout();

		Map<Integer, Float> map = new HashMap<>();

		if (strOut == null || strOut.equals(""))
			return map;

		/**
		 * ^\s*(\d)\s+([0-9.]+)\sV\s+([0-9\.]+)\sV$
		 */

		Pattern p = Pattern.compile("\\s*(\\d)\\s+(-?[0-9.]+)\\sV\\s+(-?[0-9.]+)\\sV", Pattern.MULTILINE);

		Matcher m = p.matcher(strOut);

		//int channel = 1;

		while (m.find()) {

			//System.out.println(m.group(0));
			int c = Integer.parseInt(m.group(1));
			float v1 = Float.parseFloat(m.group(2));
			float v2 = Float.parseFloat(m.group(3));

			//if (c != channel) {
				//System.out.println("Error: Channel Value Mismatch");
			//} else {

				//System.out.printf("Channel: %d, v1: %.2f, v1: %.2f\n", c, v1, v2);

				map.put(c, v1);

			//	channel++;
			//}
		}

		return map;
	}


	/**
	 * Sends a string to the device and waits to receive the response string
	 * @param str String to send
	 * @return Response
	 * @throws IOException
	 */
    private String write(String str) throws IOException {
        if (m_SerialPort == null || !m_SerialPort.isConnected())
            return "";

        //if (m_SerialOutStream == null)
        //    throw new IOException("Could not write to stream");

        // Should I be using DataOutputStream writeUTF? No, it prepends a length encoding..
        // writeChars uses 2-byte characters, so we have to use getBytes("US-ASCII")

        m_SerialPort.getOutputStream().write(str.getBytes("US-ASCII"));

		// This thing is sloooooow
		try {
			Thread.sleep(150);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// Capture the response, this device is very slow, we read a character at a time with a 1ms delay between...

		StringBuilder strB = new StringBuilder();

		do {

			try {
				strB.append(Character.toString((char)inBuf.read()));
				Thread.sleep(1);

			} catch (IOException e) {
				//System.out.println(">>EOL");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} while (inBuf.ready());

		//System.out.println(strB.toString());

		return strB.toString();
    }

    /*
    void scanAndPopulatePortList() {
        // Populate Comm Port combo box
        String portList = "";
        for (String port : getAvailablePorts()) {
            portList += port + System.getProperty("line.separator");
        }
        tlsh.getKmaxWidget("cboHVCommPort").setProperty("ITEMS", portList);

        // Try to select the most likely candidate
        tlsh.getKmaxWidget("cboHVCommPort").setProperty("ITEM", "COM6");
    }
    */

	@Deprecated
    void setVoltageEnable(boolean enable) {

        String str = String.format("k0=%d\r", enable ? 1 : 0);

        try {
            write(str);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


	public void setAllVoltages(int voltage) {
		if (m_SerialPort == null || !m_SerialPort.isConnected())
			return;

		voltage = Math.min(voltage, 2000);

		String outStr = String.format("s12v%d\r", voltage);

		try {
			write(outStr);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


    public void setChannelVoltage(int channel, int voltage) {
        if (m_SerialPort == null || !m_SerialPort.isConnected())
            return;

        voltage = Math.min(voltage, 2000);

        String outStr = String.format("s%dv%d\r", channel, voltage);

        try {
            write(outStr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


	String requestReadout() {
		if (m_SerialPort == null || !m_SerialPort.isConnected())
			return "";

		String outStr = "";

		try {
			outStr = write("r\r");
		} catch (IOException e) {}

		return outStr;
	}
}
