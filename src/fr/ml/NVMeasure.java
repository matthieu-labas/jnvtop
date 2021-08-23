package fr.ml;

import java.awt.Color;
import java.awt.GridLayout;
import java.io.IOException;

import javax.swing.JPanel;

public class NVMeasure extends JPanel implements Runnable {
	
	private static final long serialVersionUID = 1L;
	
	private PanelTimeGraph grafGpuRam, grafTempPow;
	
	public NVMeasure() {
		super(new GridLayout(2, 1));
		grafGpuRam = new PanelTimeGraph("GPU/RAM", 60, "GPU", "RAM");
		add(grafGpuRam);
		grafTempPow = new PanelTimeGraph("Temperature/Power", 60, "Temperature", "Power");
		add(grafTempPow);
		
		grafTempPow.color("Temperature", Color.RED);
		grafTempPow.color("Power", Color.GREEN);
		}
	
	@Override
	public void run() {
		try {
			NVState status = NVTop.measure();
			System.out.println(status);
			if (grafGpuRam.nbMeasures() == 0) {
				grafGpuRam.range("GPU", 0, 100);
				grafGpuRam.unit("GPU", "%");
				grafGpuRam.range("RAM", 0, status.memTotal);
				grafGpuRam.unit("RAM", status.memUnit);
			}
			
			if (grafTempPow.nbMeasures() == 0) {
				grafTempPow.range("Temperature", 0, status.tempStop);
				grafTempPow.unit("Temperature", "°C");
				grafTempPow.range("Power", 0, 100); // FIXME: Doesn't work if max is unset
				grafTempPow.unit("Power", status.powerUnit);
			}
			grafGpuRam.addValues(status.timestamp, 100*status.cpu, status.memUsed);
			grafTempPow.addValues(status.timestamp, status.temp, status.power);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
