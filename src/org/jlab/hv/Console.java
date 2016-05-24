package org.jlab.hv;

import gnu.io.NoSuchPortException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by john on 11/20/15.
 *
 */
public class Console {

    public static void main(String args[]) {

		int tV1 = -785;
		int tV2 = -840;

		int polarity = tV1 < 0 ? -1 : 1;

		List<String> ports = RTIHVSupply.GetAvailablePorts();

		for (String port : ports) {
			System.out.println(port);
		}

		RTIHVSupply hv = null;

		try {
			hv = new RTIHVSupply("/dev/tty.usbserial-HVZ24JWI", 19200);
		} catch (IOException e) {
			e.printStackTrace();
		}

		Map<Integer, Float> vMap;

		hv.setChannelVoltage(1, tV1);
		hv.setChannelVoltage(2, tV2);

		float v1t, v2t;

		do {

			vMap = hv.getVoltages();

			v1t = vMap.get(1);
			v2t = vMap.get(2);

			System.out.printf("Literal v1: %.2f   v2: %.2f\n", v1t, v2t);

			//System.out.printf("Rounded v1: %d   v2: %d\n\n", (int)(v1t + (.5 * polarity)), (int)(v2t + (.5 * polarity)));

		} while ((int)(v1t + (.5 * polarity)) != tV1 && (int)(v2t + (.5 * polarity)) != tV2);

		System.out.println("Voltages matched");

		hv.disconnect();

    }
}
