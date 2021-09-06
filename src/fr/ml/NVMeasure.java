package fr.ml;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JPanel;

public class NVMeasure extends JPanel {
	
	private static final long serialVersionUID = 1L;
	
	static public int parseInt(Properties conf, String key, int defVal) {
		try {
			return Integer.valueOf(conf.getProperty(key, ""+defVal));
		} catch (NumberFormatException e) {
			System.err.println(key+": "+e.getMessage()+", setting to default "+defVal);
			return defVal;
		}
	}
	
	/** List of graphs associated with queries (key:query). */
	private List<GraphQueryLink> graphsQ;
	
	public NVMeasure(Properties conf) {
		super();
		
		graphsQ = new ArrayList<>();
		
		// Search for number of graphs
		int imax = -1, imin = Integer.MAX_VALUE;
		Pattern pat = Pattern.compile("graph\\.([0-9]+)\\.");
		for (Object o : conf.keySet()) {
			String k = (String)o;
			Matcher m = pat.matcher(k);
			if (!m.find()) {
				continue;
			}
			int i = Integer.parseInt(m.group(1));
			imin = Math.min(imin, i);
			imax = Math.max(imax, i);
		}
		
		int nGraphs = imax - imin + 1; // How many graphs
		String layout = conf.getProperty("graph.grid", nGraphs+","+1);
		int rows = nGraphs, cols = 1;
		try {
			String[] rowsCols = layout.split(",");
			rows = Integer.parseInt(rowsCols[0]);
			if (rowsCols.length < 2) {
				cols = Math.max(1, nGraphs / rows);
			} else {
				cols = Integer.parseInt(rowsCols[1]);
			}
		} catch (NumberFormatException e) {
			System.err.println("Malformed \"graph.grid\" format '"+layout+"': should be <rows>,<cols> (e.g. \"2,1\"): "+e.getMessage());
			rows = nGraphs;
			cols = 1;
		}
		setLayout(new GridLayout(rows, cols));
		
		int duration = parseInt(conf, "graph.duration", 60);
		int majorY = parseInt(conf, "graph.ticks.majors", 5);
		int minorY = parseInt(conf, "graph.ticks.minors", 0);
		int timeTicks = parseInt(conf, "graph.ticks.time", 10) * 1000; // From s to ms
		Color bckColor = Color.decode(conf.getProperty("graph.background.panel", "#c0c0c0"));
		Color defBckColor = Color.decode(conf.getProperty("graph.background", "#c0c0c0"));
		Color defTickColor = Color.decode(conf.getProperty("graph.ticks.color", "#c0c0c0")); // Default ticks color
		
		PanelTimeGraph.timeFormat = conf.getProperty("graph.ticks.time.format", "HH:mm:ss");
		PanelTimeGraph.titleFont = Font.decode(conf.getProperty("graph.title.font", "Tahoma-bold-12"));
		
		for (int ig = imin; ig <= imax; ig++) {
			String graphi = "graph."+ig+".";
			
			// Check which series are configured
			List<String> series = new ArrayList<>(2); // Can't use Arrays.asList() as it's a read-only list that doesn't support removal
			series.add("left");
			series.add("right");
			for (Iterator<String> it = series.iterator(); it.hasNext(); ) {
				if (!conf.containsKey(graphi+it.next()+".query")) {
					it.remove();
				}
			}
			
			// 'series' now contains only the "left" or "right" that are defined in the configuration
			int nSeries = series.size();
			
			// Get series query and title
			String[] queries = new String[nSeries];
			String[] titles = new String[nSeries];
			for (int i = 0; i < nSeries; i++) {
				String lr = graphi+series.get(i);
				queries[i] = conf.getProperty(lr+".query");
				titles[i] = conf.getProperty(lr+".title", queries[i].replace('.', ' '));
			}
			
			PanelTimeGraph graph = new PanelTimeGraph(conf.getProperty(graphi+"title", "Graph #"+ig), duration, titles);
			add(graph);
			
			// General configuration
			graph.setBackground(bckColor);
			graph.yTicks(majorY, minorY);
			graph.timeTicks(timeTicks);
			graph.background(parseColor(conf, graphi+"background", defBckColor));
			graph.ticksColor(parseColor(conf, graphi+"ticks.color", defTickColor));
			
			// Create the link between the graph and its queries
			graphsQ.add(new GraphQueryLink(graph, queries));
			
			// Configure graph attributes
			for (int i = 0; i < nSeries; i++) {
				String k = graphi+series.get(i)+".";
				int _is = i;
				apply(conf, k+"min"  , Float::valueOf     , val -> graph.min(_is, val));
				apply(conf, k+"max"  , Float::valueOf     , val -> graph.max(_is, val));
				apply(conf, k+"unit" , Function.identity(), val -> graph.unit(_is, val));
				apply(conf, k+"color", Color::decode      , val -> graph.color(_is, val));
			}
		}
	}
	
	static private Color parseColor(Properties conf, String key, Color defColor) {
		String strCol = conf.getProperty(key);
		if (strCol != null && !strCol.isEmpty()) {
			try {
				return Color.decode(strCol);
			} catch (NumberFormatException e) {
				System.err.println("Cannot parse color \""+key+"\": "+e.getMessage());
			}
		}
		return defColor;
	}
	
	/**
	 * Get the given property and, if it exists, transforms it into {@code <T>} and
	 * feeds it to the given function {@code f}.
	 * @param <T> The type to transform the property into.
	 * @param p The properties file.
	 * @param k The property key.
	 * @param trans The transformation from {@code String} to {@code <T>} (e.g. {@link Float#valueOf(String)}).
	 * @param f The function to call with the transformed value.
	 */
	static private <T> void apply(Properties p, String k, Function<String,T> trans, Consumer<T> f) {
		String s = (String)p.getProperty(k);
		if (s != null) {
			try {
				f.accept(trans.apply(s));
			} catch (Exception e) {
				System.err.println(k+": "+e.getMessage()); // TODO: JOptionPane ?
			}
		}
	}
	
	public void pushMeasures(long t, Map<String,Float> measures) {
		// Push data first ...
		for (GraphQueryLink gql : graphsQ) {
			gql.push(t, measures);
		}
		// ... then repaint all graphs at once (they are contained in this panel)
		repaint();
	}
	
	
	
	static private class GraphQueryLink {
		
		private PanelTimeGraph graph;
		private String[] queries;
		
		private GraphQueryLink(PanelTimeGraph graph, String ... queries) {
			this.graph = graph;
			this.queries = queries;
		}
		
		private void push(long t, Map<String,Float> values) {
			int nSeries = queries.length;
			float[] vals = new float[nSeries];
			for (int i = 0; i < nSeries; i++) {
				vals[i] = values.getOrDefault(queries[i], Float.NaN);
			}
			graph.addValues(t, vals);
		}
		
	}
	
}
