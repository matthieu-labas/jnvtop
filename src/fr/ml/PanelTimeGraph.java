package fr.ml;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.function.Supplier;

import javax.swing.JPanel;

// TODO: Use baseline to adjust Y when drawing strings

public class PanelTimeGraph extends JPanel {
	
	private static final long serialVersionUID = 1L;
	
	/** The format used to display timestamp, using {@link SimpleDateFormat} format.
	 * This field is common to all graph panels, for consistency. */
	static public String timeFormat = "HH:mm:ss";
	
	/** The font for graphs titles.
	 * This field is common to all graph panels, for consistency. */
	static public Font titleFont = new Font("Tahoma", Font.BOLD, 12);
	
	static private Stroke dashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0.0f, new float[]{2.5f, 5.0f}, 0.0f);
	static private Stroke plainStroke = new BasicStroke(1);
	
	static Dimension strDim(Graphics2D g, String str) {
		TextLayout tl = new TextLayout(str, g.getFont(), g.getFontRenderContext());
		Rectangle r = tl.getBounds().getBounds();
		return new Dimension(r.width, r.height);
	}
	
	
	private String title;
	
	/** Graph background color. */
	private Color bckColor;
	/** Ticks color. */
	private Color ticksColor = Color.LIGHT_GRAY;
	
	/** Duration graphed, ms. */
	private int duration;
	
	/** Series names. */
	private String[] series;
	/** Series units. */
	private String[] units;
	/** Manual min/max. {@code Float.NaN} if unset. */
	private float[] max, min;
	/** Current min/max for each series. */
	private float[] curMin, curMmax;
	/** Series colors. */
	private Color[] colors = new Color[] { Color.BLUE, Color.RED }; // Par défaut
	
	/** Number of scale marks to divide the Y axis into. */
	private int nTicksMajorY = 5, nTicksMinorY = 10;
	
	/** Show a mark along the X axis every {@code tMarksX} ms. */
	private int tTicksX = 1000; // Every second
	
	/** Series values (Y axis). */
	private float[][] values;
	/** Timestamps (X axis). */
	private long[] ts;
	
	private int inext;
	
	/** Clock to give current time. */
	private Supplier<Long> clock;
	
	public PanelTimeGraph(Supplier<Long> clock, String title, int duration_s, String ... series) throws IllegalArgumentException {
		super(new BorderLayout());
		
		if (series.length > 2) {
			throw new IllegalArgumentException("Cannot graph more than 2 values (at the moment...): "+series);
		}
		
		if (clock == null) {
			clock = System::currentTimeMillis;
		}
		this.clock = clock;
		
		this.title = title;
		this.duration = duration_s * 1000; // In ms
		
		this.series = Arrays.stream(series)
				.filter(s -> s != null)
				.toArray(n -> new String[n]); // Removes nulls from the series
		int nSeries = series.length;
		units = new String[nSeries];
		curMin = new float[nSeries];
		curMmax = new float[nSeries];
		
		min = new float[nSeries];
		max = new float[nSeries];
		for (int i = 0; i < nSeries; i++) {
			min[i] = max[i] = Float.NaN;
			curMin[i] = Float.MAX_VALUE;
			curMmax[i] = -Float.MAX_VALUE;
			units[i] = "";
		}
		
		ts = new long[60]; // One minute if one measure per second
		values = new float[nSeries][ts.length];
		
		inext = 0;
		
		setBackground(Color.LIGHT_GRAY);
	}
	
	public PanelTimeGraph(String title, int duration_s, String ... series) throws IllegalArgumentException {
		this(System::currentTimeMillis, title, duration_s, series);
	}
	
	public PanelTimeGraph title(String title) {
		this.title = title;
		return this;
	}
	
	public PanelTimeGraph background(Color bckColor) {
		this.bckColor = bckColor;
		return this;
	}
	
	public PanelTimeGraph ticksColor(Color c) {
		this.ticksColor = c;
		return this;
	}
	
	public PanelTimeGraph yTicks(int nMajor, int nMinor) {
		this.nTicksMajorY = nMajor;
		this.nTicksMinorY = nMinor;
		return this;
	}
	
	public PanelTimeGraph timeTicks(int tTicks) {
		this.tTicksX = tTicks;
		return this;
	}
	
	private int iSerie(String serie) throws IllegalArgumentException {
		for (int i = 0; i < series.length; i++) {
			if (series[i].equals(serie)) {
				return i;
			}
		}
		throw new IllegalArgumentException("Unknown series '"+serie+"' (known series are "+Arrays.toString(series)+")");
	}
	
	public PanelTimeGraph min(int iSerie, float min) {
		this.min[iSerie] = min;
		return this;
	}
	public PanelTimeGraph min(String serie, float min) throws IllegalArgumentException {
		return min(iSerie(serie), min);
	}
	
	public PanelTimeGraph max(int iSerie, float max) {
		this.max[iSerie] = max;
		return this;
	}
	public PanelTimeGraph max(String serie, float max) throws IllegalArgumentException {
		return max(iSerie(serie), max);
	}
	
	public PanelTimeGraph range(int iSerie, float min, float max) {
		this.min[iSerie] = min;
		this.max[iSerie] = max;
		return this;
	}
	public PanelTimeGraph range(String serie, float min, float max) throws IllegalArgumentException {
		return range(iSerie(serie), min, max);
	}
	
	public PanelTimeGraph unit(int iSerie, String unite) {
		this.units[iSerie] = unite;
		return this;
	}
	public PanelTimeGraph unit(String serie, String unite) {
		return unit(iSerie(serie), unite);
	}
	
	public PanelTimeGraph color(int iSerie, Color coul) {
		if (coul != null) {
			this.colors[iSerie] = coul;
		}
		return this;
	}
	public PanelTimeGraph color(String serie, Color coul) {
		return color(iSerie(serie), coul);
	}
	
	/**
	 * Call this method every second or so to handle forgetting old data.
	 * @return The number of forgotten data (and so an indication that calling {@code repaint()}
	 * 		would be a good idea if {@code > 0}).
	 */
	synchronized public int forget() {
		long t0 = clock.get();
		int i = 0;
		while (i < inext && (int)(t0 - ts[i]) > duration) {
			i++;
		}
		if (i > 0) {
			inext -= i;
			System.arraycopy(ts, i, ts, 0, inext); // FIXME: Make it a better FIFO: (istart;iend) instead of ugly memmove
			for (int j = 0; j < values.length; j++) {
				System.arraycopy(values[j], i, values[j], 0, inext);
				// Recalcul des minCou/maxCou sur les données non supprimées
				curMin[j] = Float.MAX_VALUE;
				curMmax[j] = -Float.MAX_VALUE;
				for (int k = 0; k < inext; k++) {
					float v = values[j][k];
					if (!Float.isNaN(v)) {
						if (v < curMin[j]) curMin[j] = v;
						if (v > curMmax[j]) curMmax[j] = v;
					}
				}
			}
		}
		return i;
	}
	
	synchronized public void addValues(long t, float ... vals) throws IllegalArgumentException {
		int nVal = vals.length;
		// Check number of data consistency
		if (nVal != values.length) {
			throw new IllegalArgumentException("Number of given data ("+nVal+") inconsistent with expected number ("+values.length+")");
		}
		// Check timestamp is increasing
		if (inext > 0 && ts[inext-1] >= t) {
			throw new IllegalArgumentException("Given timestamp ("+t+") is before last one by "+(ts[inext-1] - t)+" ms");
		}
		forget(); // Before adding data, if it can free up some space...
		ts[inext] = t;
		for (int i = 0; i < nVal; i++) {
			float v = vals[i];
			values[i][inext] = v;
			if (!Float.isNaN(v)) {
				if (v > curMmax[i]) curMmax[i] = v;
				if (v < curMin[i]) curMin[i] = v;
			}
		}
		if (++inext >= values[0].length) {
			for (int i = 0; i < nVal; i++) {
				values[i] = Arrays.copyOf(values[i], values[i].length + 60); // Add one minute (if sampling is at 1 Hz)
			}
			ts = Arrays.copyOf(ts, values[0].length);
		}
	}
	
	synchronized public void setValue(long t, int index, float v) throws IllegalArgumentException {
		if (index < 0 || index >= values.length) {
			throw new IllegalArgumentException("Data index ("+index+") inconsistent with actual number of series ("+values.length+")");
		}
		
		// Look for the corresponding timestamp
		for (int i = inext - 1; i >= 0; i--) {
			if (ts[i] == t) {
				values[index][i] = v;
				if (!Float.isNaN(v)) { // FIXME: create method
					if (v > curMmax[index]) curMmax[index] = v;
					if (v < curMin[index]) curMin[index] = v;
				}
				return;
			}
		}
		
		// Timestamp not found => add value
		ts[inext] = t;
		if (inext++ >= values[0].length) { // FIXME: create method
			for (int i = 0; i < values.length; i++) {
				values[i] = Arrays.copyOf(values[i], values[i].length + 60); // Add one minute (if sampling is at 1 Hz)
			}
			ts = Arrays.copyOf(ts, values[0].length);
		}
		
		throw new IllegalArgumentException("Data index ("+index+") inconsistent with actual number of series ("+values.length+")");
	}
	
	synchronized public void addValues(float ... vals) throws IllegalArgumentException {
		addValues(clock.get(), vals);
	}
	
	public int nbMeasures() {
		return inext;
	}
	
	static private void drawYTicks(Graphics2D g, Stroke s, int w, int h, int offX, int offY, int nTicks) {
		Stroke ols = null;
		if (s != null) {
			ols = g.getStroke();
			g.setStroke(s);
		}
		float ry = (float)(h - 2*offY) / nTicks;
		for (int i = 1; i < nTicks; i++) {
			int y = offY + Math.round(i * ry);
			g.drawLine(offX+1, y, w - offX, y);
		}
		if (ols != null) {
			g.setStroke(ols);
		}
	}
	
	@Override
	public void paintComponent(Graphics g1) {
		super.paintComponent(g1);
		
		Graphics2D g = (Graphics2D)g1;
		int w = getWidth();
		int h = getHeight();
		
		// Empty space to draw axis and text
		int offX = 80; // So x = 'w - offX' is the current date, x = 'offX' is current date minus duration
		int offY = 50;
		
		Color txtColor = textColor(getBackground());
		
		if (title != null && !title.isBlank()) {
			g.setColor(txtColor);
			Font olf = null;
			if (titleFont != null) {
				olf = g.getFont();
				g.setFont(titleFont);
			}
			Dimension d = strDim(g, title);
			g.drawString(title, (w - d.width) / 2, offY - d.height);
			if (olf != null) {
				g.setFont(olf);
			}
		}
		
		// Draw series names
		int nSeries = series.length;
		for (int i = 0; i < nSeries; i++) {
			if (series[i] == null || series[i].isBlank()) {
				continue;
			}
			Color olc = g.getColor();
			Dimension d = strDim(g, series[i]);
			g.setColor(colors[i]);
			g.drawString(series[i], i == 0 ? offX - d.width / 2 : w - offX - d.width / 2, offY - d.height/2);
			g.setColor(olc);
		}
		
		// Nothing to graph
		if (inext == 0) {
			return;
		}
		
		g.setColor(bckColor);
		g.fillRect(offX, offY, w - 2*offX, h - 2*offY);
		g.setColor(txtColor);
		g.drawRect(offX, offY, w - 2*offX, h - 2*offY);
		
		if (nTicksMinorY > 0) {
			g.setColor(new Color(ticksColor.getRed(), ticksColor.getGreen(), ticksColor.getBlue(), 64));
			drawYTicks(g, dashedStroke, w, h, offX, offY, nTicksMinorY);
		}
		// Major ticks on top (FIXME: do not draw the minors matching a major)
		if (nTicksMajorY > 0) {
			g.setColor(new Color(ticksColor.getRed(), ticksColor.getGreen(), ticksColor.getBlue(), 128));
			drawYTicks(g, plainStroke, w, h, offX, offY, nTicksMajorY);
		}
		
		float rx = (float)(w - 2*offX) / duration; // Ratio to transform time to pixels
		
		long t = clock.get(); // Rightmost timestamp
		
		// Timestamp sticks
		if (tTicksX > 0) {
			g.setColor(ticksColor);
			Stroke ols = g.getStroke();
			g.setStroke(plainStroke);
			Font olf = g.getFont();
			AffineTransform at = new AffineTransform();
			at.scale(0.9, 0.9);
			at.rotate(-Math.PI / 8);
			g.setFont(olf.deriveFont(at));
			Color cTick = new Color(192, 192, 192, 255);
			DateFormat df = new SimpleDateFormat(timeFormat);
			long t0 = t - duration; // Left-most timestamp
			for (long xt = t - (t % tTicksX); xt > t0; xt -= tTicksX) {
				int x = w - offX - Math.round((t - xt) * rx);
				g.setColor(cTick);
				g.drawLine(x, h - offY - 1, x, offY + 1);
				// Print timestamp
				g.setColor(txtColor);
				String ts = df.format(new Date(xt));
				Dimension dim = strDim(g, ts);
				g.drawString(ts, x - dim.width/2, h - offY + dim.height + 3);
			}
			g.setFont(olf);
			g.setStroke(ols);
		}
		
		// Compute x for each timestamp according to current date 't'
		int[] xts = new int[inext];
		for (int ix = 0; ix < inext; ix++) {
			xts[ix] = w - offX - Math.round((t - ts[ix]) * rx);
		}
		
		for (int is = nSeries - 1; is >= 0; is--) { // First series on top
			// Min/max to graph
			float min = this.min[is];
			if (Float.isNaN(min)) min = this.curMin[is];
			if (Float.isNaN(min)) continue;
			float max = this.max[is];
			if (Float.isNaN(max)) max = this.curMmax[is];
			if (Float.isNaN(max)) continue;
			
			float ry = (h - 2*offY) / (max -min);
			
			int x0 = -1;
			int y0 = -1;
			
			g.setColor(colors[is]);
			for (int ix = 0; ix < inext; ix++) {
				int k = inext - ix - 1; // From right to left
				float v = values[is][k];
				if (Float.isNaN(v)) {
					x0 = -1; // Empty value => stop lines
					continue;
				}
				
				int x = xts[k];
				int y = h - offY - Math.round(v * ry);
				if (x0 < 0) { // First point (or after an empty value) => circle
					g.fillOval(x-2, y-2, 5, 5);
				} else {
					g.drawLine(x0, y0, x, y);
				}
				x0 = x;
				y0 = y;
			}
			// Recall current value accross the whole width
			g.setColor(new Color(colors[is].getRed(), colors[is].getGreen(), colors[is].getBlue(), 32)); // Transparent
			int y = h - offY - Math.round(values[is][inext - 1] * ry);
			g.drawLine(offX, y, w - offX, y); // TODO: Up to last valid value : if last value is missing (NaN), w - offX - <last good>
			
			// Draw max
			String s = String.format("%.0f%s", max, units[is]);
			int x = (is == 0 ? offX : w - offX);
			drawStringBack(g, s, null, colors[is], x, is == 0, offY, false); // Max is aligned on top. First series axis is on the left (aligned on the right)
			
			// Draw min
			s = String.format("%.0f%s", min, units[is]);
			drawStringBack(g, s, null, colors[is], x, is == 0, h - offY, true); // Min is aligned on bottom
			
			// Display current values in the center right, on top of each other
			if (inext > 0) {
				s = String.format("%.0f%s", values[is][inext-1], units[is]);
				drawStringBack(g, s, colors[is], null, w - offX, false, h/2, is == 0); // No background for current values
			}
		}
	}
	
	static public float perceivedLuminance(Color c) {
		return 0.299f * c.getRed() + 0.587f * c.getGreen() + 0.114f * c.getBlue();
	}
	static public Color textColor(Color bk) {
		return perceivedLuminance(bk) >= 128 ? Color.BLACK : Color.WHITE;
	}
	
	static public Dimension drawStringBack(Graphics2D g, String s, Color back, Color text, int x, boolean alignRight, int y, boolean alignBottom) {
		Color olc = g.getColor();
		
		Dimension d = strDim(g, s);
		// 3 pixels around
		d.width += 6;
		d.height += 6;
		
		if (alignRight) {
			x -= d.width;
		}
		if (alignBottom) {
			y -= d.height;
		}
		
		if (back != null) {
			g.setColor(back);
			g.fillRect(x, y, d.width, d.height);
		}
		g.setColor(text == null ? Color.BLACK : text);
		g.drawRect(x, y, d.width, d.height);
		if (back != null) {
			g.setColor(textColor(back));
		}
		g.drawString(s, x+3, y-3 + d.height);
		
		g.setColor(olc);
		
		return d;
	}
	
}