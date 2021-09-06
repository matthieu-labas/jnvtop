package fr.ml;

import java.awt.BorderLayout;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class NVTop {
	
	/*
	 * nvidia-smi --help-query-gpu cheat sheet:
	 * 
	 * Kind of mandatory (useful for any measure required):
	 * "timestamp"	The timestamp of when the query was made in format "YYYY/MM/DD HH:MM:SS.msec".
	 * "name"		The official product name of the GPU. This is an alphanumeric string. For all products.
	 * 
	 * Useful measures:
	 * "fan.speed"			The fan speed value is the percent of the product's maximum noise tolerance fan speed that the device's fan is currently intended to run at.
	 * "pstate"				The current performance state for the GPU. States range from P0 (maximum performance) to P12 (minimum performance).
	 * 
	 * "memory.total"
	 * "memory.used"		Total memory allocated by active contexts.
	 * "memory.free"
	 * 
	 * "utilization.gpu"	Percent of time over the past sample period during which one or more kernels was executing on the GPU.
	 * "utilization.memory"	Percent of time over the past sample period during which global (device) memory was being read or written.
	 * 
	 * "temperature.gpu"	 Core GPU temperature. in degrees C.
	 * "temperature.memory"	HBM memory temperature. in degrees C.
	 * 
	 * "power.draw"			The last measured power draw for the entire board, in watts.
	 * "power.limit"		The software power limit in watts.
	 * "enforced.power.limit"
	 * "power.default_limit"
	 * "power.min_limit"
	 * "power.max_limit"
	 * 
	 * "clocks.current.graphics"	Current frequency of graphics (shader) clock.
	 * "clocks.current.sm"			Current frequency of SM (Streaming Multiprocessor) clock.
	 * "clocks.current.memory"		Current frequency of memory clock.
	 * "clocks.current.video"		Current frequency of video encoder/decoder clock.
	 * "clocks.max.graphics"		Maximum frequency of graphics (shader) clock.
	 * "clocks.max.sm"				Maximum frequency of SM (Streaming Multiprocessor) clock.
	 * "clocks.max.memory"			Maximum frequency of memory clock.
	 */
	
	/** Default configuration file. */
	static public final String defaultConf = "nvtop.properties";
	
	static final String tsFormat = "yyyy/MM/dd HH:mm:ss.SSS";
	
	public static void main(String[] args) {
		String confFile = (args.length > 0 ? args[0] : defaultConf);
		Properties prop = new Properties();
		if (!Files.exists(Paths.get(confFile))) {
			System.err.println("Cannot find configuration file "+confFile+", creating default configuration in it");
			try (FileWriter fw = new FileWriter(confFile)) {
				// Create default configuration: GPU% and RAM
				prop.put("graph.refresh", "1000"); // 1s refresh
				prop.put("graph.duration", "60"); // 1min total
				prop.put("graph.1.title", "System");
				prop.put("graph.1.left.query", "utilization.gpu");
				prop.put("graph.1.left.title", "GPU");
				prop.put("graph.1.left.min", "0");
				prop.put("graph.1.left.max", "100");
				prop.put("graph.1.left.unit", "%");
				prop.put("graph.1.right.query", "memory.used");
				prop.put("graph.1.right.title", "RAM");
				prop.put("graph.1.right.min", "0");
				prop.put("graph.1.right.max", "8192");
				prop.put("graph.1.right.unit", "MiB");
				prop.store(fw, "");
			} catch (IOException e) {
				JOptionPane.showMessageDialog(null, "Cannot create configuration: "+e.getMessage(), "Creating configuration file", JOptionPane.ERROR_MESSAGE);
			}
		} else {
			try (FileInputStream is = new FileInputStream(confFile)) {
				prop.load(is);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(null, "Cannot read configuration "+confFile+":\n"+e.getMessage(), "Reading configuration file", JOptionPane.ERROR_MESSAGE);
				return;
			}
		}
		
		JFrame f = new JFrame();
		
		NVMeasure measurePanel = new NVMeasure(prop);
		f.getContentPane().add(measurePanel, BorderLayout.CENTER);
		
		// TODO: North panel to control quick graphs configuration (duration, others?)
		// TODO: Configuration panel to adjust other parameters (colors, ticks, scales, ...)
		
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.setSize(800, 600);
		
		// List unique SMI queries
		List<String> queries = prop.keySet().stream() // Keys ...
			.filter(k -> ((String)k).endsWith(".query")) // ... representing queries ...
			.map(k -> prop.getProperty((String)k)) // ... values of those keys ...
			.distinct() // ... that are unique ...
			.collect(Collectors.toList());
		// Add timestamp as first value (remove it first in case it was there)
		queries.remove("timestamp");
		queries.add(0, "timestamp");
		
		StringBuilder sb = new StringBuilder();
		for (String q : queries) {
			if (sb.length() > 0) sb.append(',');
			sb.append(q);
		}
		
		ProcessBuilder nvsmi = new ProcessBuilder("nvidia-smi", "--query-gpu="+sb.toString(), "--format=csv,noheader,nounits", "--loop-ms="+prop.getProperty("graph.refresh", "1000"));
		sb = null; // Free
		
		try {
			Process nvtop = nvsmi.start();
			SimpleDateFormat df = new SimpleDateFormat(tsFormat);
			f.setVisible(true); // Process started => show JFrame and start parsing
			
			BufferedReader rd = new BufferedReader(new InputStreamReader(new BufferedInputStream(nvtop.getInputStream())));
			String line;
			int nQueries = queries.size();
			Map<String,Float> measures = new HashMap<>(nQueries - 1); // No timestamp
			while ((line = rd.readLine()) != null) {
				String[] values = line.trim().split(", ");
				if (values.length != nQueries) {
					JOptionPane.showMessageDialog(f, "Retrieved values number ( "+values.length+") inconsistent with expected ("+nQueries+")", "Data retrieval", JOptionPane.ERROR_MESSAGE);
					break;
				}
				
				// Read timestamp
				long ts;
				try {
					ts = df.parse(values[0]).getTime();
				} catch (ParseException e) {
					System.err.println(e.getMessage());
					ts = System.currentTimeMillis();
				}
				
				for (int i = 1; i < nQueries; i++) { // Skip timestamp
					Float v;
					if ("N/A".equalsIgnoreCase(values[i])) { // Special "N/A" case
						v = Float.NaN;
					} else {
						try {
							v = Float.valueOf(values[i]);
						} catch (NumberFormatException e) { // Invalid query (doesn't give a Float)
							System.err.println("Cannot parse query "+queries.get(i)+"="+values[i]+": "+e.getMessage());
							v = Float.NaN;
						}
					}
					measures.put(queries.get(i), v);
				}
				
//				System.out.println(ts - System.currentTimeMillis()+": "+measures);
				
				measurePanel.pushMeasures(ts, measures);
			}
			// Oops, process ended... Do not close window
		} catch (IOException e) { // Cannot start process => dispose JFrame and exit
			f.dispose();
			JOptionPane.showMessageDialog(null, "Cannot start process: "+e.getMessage(), "Starting nvidia-smi", JOptionPane.ERROR_MESSAGE);
		}
	}
	
}
