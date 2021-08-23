package fr.ml;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.TextLayout;
import java.util.Arrays;
import java.util.function.Supplier;

import javax.swing.JPanel;

public class PanelTimeGraph extends JPanel {
	
	private static final long serialVersionUID = 1L;
	
	static Dimension strDim(Graphics2D g, String str) {
		TextLayout tl = new TextLayout(str, g.getFont(), g.getFontRenderContext());
		Rectangle r = tl.getBounds().getBounds();
		return new Dimension(r.width, r.height);
	}
	
	
	private Font titleFont;
	private String title;
	
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
	private int nMarksY = 10; // 10 divisions
	
	/** Show a mark along the X axis every {@code tMarksX} ms. */
	private int tMarksX = 1000; // Every second
	
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
		
		this.series = series;
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
	
	public PanelTimeGraph titleFont(Font titleFont) {
		this.titleFont = titleFont;
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
		this.colors[iSerie] = coul;
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
		if (inext++ >= values[0].length) {
			for (int i = 0; i < nVal; i++) {
				values[i] = Arrays.copyOf(values[i], values[i].length + 60); // Add one minute (if sampling is at 1 Hz)
			}
			ts = Arrays.copyOf(ts, values[0].length);
		}
		repaint();
	}
	
	synchronized public void addValues(float ... vals) throws IllegalArgumentException {
		addValues(clock.get(), vals);
	}
	
	public int nbMeasures() {
		return inext;
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
		
		if (title != null && !title.isBlank()) {
			Font olf = null;
			if (titleFont != null) {
				olf = g.getFont();
				g.setFont(titleFont);
			}
			Dimension d = strDim(g, title);
			g.drawString(title, (w - d.width) / 2, offY - d.height); // TODO: Use baseline to adjust Y
			if (olf != null) {
				g.setFont(olf);
			}
		}
		
		// Nothing to graph
		if (inext == 0) {
			return;
		}
		
		g.setColor(Color.WHITE);
		g.fillRect(offX, offY, w - 2*offX, h - 2*offY);
		g.setColor(Color.BLACK);
		g.drawRect(offX, offY, w - 2*offX, h - 2*offY);
		
		if (nMarksY > 0) {
			g.setColor(new Color(192, 192, 192, 64));
			float ry = (float)(h - 2*offY) / nMarksY;
			for (int i = 1; i < nMarksY; i++) {
				int y = offY + Math.round(i * ry);
				g.drawLine(offX+1, y, w - offX, y);
			}
		}
		
		if (tMarksX > 0) {
			// TODO
		}
		
		float rx = (float)(w - 2*offX) / duration;
		int nSeries = series.length;
		
		long t = clock.get(); // Rightmost timestamp
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
			g.setColor(new Color(colors[is].getRed(), colors[is].getGreen(), colors[is].getBlue(), 64)); // Transparent
			int y = h - offY - Math.round(values[is][inext - 1] * ry);
			g.drawLine(offX, y, w - offX, y); // TODO: Up to last valid value : if last value is missing (NaN), w - offX - <last good>
			
			// Draw max
			String s = String.format("%.0f%s", max, units[is]);
			int x = (is == 0 ? offX : w - offX);
			drawStringBack(g, s, colors[is], x, is == 0, offY, false); // Max is aligned on top. First series axis is on the left (aligned on the right)
			
			// Draw min
			s = String.format("%.0f%s", min, units[is]);
			drawStringBack(g, s, colors[is], x, is == 0, h - offY, true); // Min is aligned on bottom
			
			// Display current values in the center right, on top of each other
			if (inext > 0) {
				s = String.format("%.0f%s", values[is][inext-1], units[is]);
				drawStringBack(g, s, colors[is], w - offX, false, h/2, is == 0);
			}
		}
		
		g.setColor(Color.BLACK);
	}
	
	static public float perceivedLuminance(Color c) {
		return 0.299f * c.getRed() + 0.587f * c.getGreen() + 0.114f * c.getBlue();
	}
	static public Color textColor(Color bk) {
		return perceivedLuminance(bk) >= 128 ? Color.BLACK : Color.WHITE;
	}
	
	static public Dimension drawStringBack(Graphics2D g, String s, Color back, int x, boolean alignRight, int y, boolean alignBottom) {
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
		
		// TODO: Use baseline to adjust Y
		g.setColor(back);
		g.fillRect(x, y, d.width, d.height);
		g.setColor(Color.BLACK);
		g.drawRect(x, y, d.width, d.height);
		g.setColor(textColor(back));
		g.drawString(s, x+3, y-3 + d.height);
		
		g.setColor(olc);
		
		return d;
	}
	
}