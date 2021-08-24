package fr.ml;

import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
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
		setLayout(new GridLayout(nGraphs, 1)); // TODO: "graph.<n>.[x|y] for finer grid positionning instead of one on top of each other
		
		int duration;
		try {
			duration = Integer.valueOf(conf.getProperty("graph.duration", "60"));
		} catch (NumberFormatException e) {
			duration = 60;
			System.err.println("graph.duration: "+e.getMessage()+", setting to default "+duration); // TODO: JOptionPane ?
		}
		
		for (int ig = imin; ig <= imax; ig++) {
			String graphi = "graph."+ig+".";
			
			// Check which series are configured
			List<String> series = Arrays.asList("left", "right");
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
		// ... then repaint
		for (GraphQueryLink gql : graphsQ) {
			gql.graph.repaint();
		}
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
