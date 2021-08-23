package fr.ml;

import java.awt.BorderLayout;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;

public class NVTop {
	
	
	
	static ProcessBuilder pb = new ProcessBuilder("nvidia-smi", "-q"); // TODO: nvidia-smi --help-query-gpu et nvidia-smi --query-gpu=utilization.memory,memory.used,temperature.memory,temperature.gpu --format=csv,noheader -lms 500
	static ScheduledExecutorService exe = Executors.newSingleThreadScheduledExecutor();
	
	public static void main(String[] args) throws IOException {
		JFrame f = new JFrame();
		
		NVMeasure measure = new NVMeasure();
		f.getContentPane().add(measure, BorderLayout.CENTER);
		
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.setSize(800, 600);
		f.setVisible(true);
		
		exe.scheduleAtFixedRate(measure, 500, 1000, TimeUnit.MILLISECONDS); // TODO: -lms parameter
	}
	
	
	static public NVState measure() throws IOException {
		Process nvtop = pb.start();
		NVState status = new NVState(); // After starting process (to initialize timestamp)
		BufferedReader rd = new BufferedReader(new InputStreamReader(new BufferedInputStream(nvtop.getInputStream())));
		String lig;
		while ((lig = rd.readLine()) != null) {
			lig = lig.trim();
			switch (lig) {
				case "FB Memory Usage": {
					TitleValUnit<Integer> total = TitleValUnit.readInteger(rd);
					TitleValUnit<Integer> used = TitleValUnit.readInteger(rd);
					status.memUnit = total.unit;
					status.memTotal = total.value.intValue();
					status.memUsed = used.value.intValue();
				} continue;
				
				case "Utilization": {
					TitleValUnit<Integer> gpu = TitleValUnit.readInteger(rd);
//					TitleValUnit<Integer> mem = TitleValUnit.readInteger(rd); // We dont need mem% (easy to compute)
					status.cpu = gpu.value / 100f;
				} continue;
				
				case "Temperature": {
					TitleValUnit<Integer> current = TitleValUnit.readInteger(rd);
					TitleValUnit<Integer> shutdown = TitleValUnit.readInteger(rd);
					TitleValUnit<Integer> slowdown = TitleValUnit.readInteger(rd);
					status.temp = current.value.intValue();
					status.tempSlow = slowdown.value.intValue();
					status.tempStop = shutdown.value.intValue();
				} continue;
				
				case "Power Readings": {
					rd.readLine(); // Skip "Power Management"
					TitleValUnit<Float> power = TitleValUnit.readFloat(rd);
					status.power = power.value.floatValue();
					status.powerUnit = power.unit;
				} continue;
				
				case "Clocks": {
					// TODO
				} continue;
				
				default:
					break;
			}
		}
		while (nvtop.isAlive()) {
			try {
				nvtop.waitFor();
			} catch (InterruptedException e) {
				Thread.interrupted();
			}
		}
		
		return status;
	}
	
}
