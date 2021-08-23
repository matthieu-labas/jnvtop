import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import fr.ml.PanelTimeGraph;

public class TestNVTop {
	
	public static void main(String[] args) {
		testGraph();
	}
	
	static private class Clk implements Supplier<Long> {
		long t = 0;
		void plus_ms(int delta) { t += delta; }
		@Override public Long get() { return t; }
	}
	
	static void testGraph() {
		JFrame f = new JFrame();
		
		Clk clk = new Clk(); // Manual clock for tests
		
		JPanel pCtl = new JPanel();
		f.getContentPane().add(pCtl, BorderLayout.NORTH);
		JButton bPlus500 = new JButton("+CLK");
		pCtl.add(bPlus500);
		JButton bData = new JButton("+DATA");
		pCtl.add(bData);
		
		PanelTimeGraph graf = new PanelTimeGraph(clk, "CPU/RAM", 5, "CPU", "RAM"); // 5 seconds
		f.getContentPane().add(graf, BorderLayout.CENTER);
		
		bPlus500.addActionListener(e -> {
			clk.plus_ms(500);
			graf.forget();
			graf.repaint();
		});
		
		bData.addActionListener(new ActionListener() {
			Random r = new Random(0);
			float lastCPU = r.nextFloat();
			float lastRAM = r.nextInt(4096);
			
			@Override
			public void actionPerformed(ActionEvent e) {
				float delta = (r.nextFloat() - .5f) / 10;
				lastCPU += delta;
				if (lastCPU > 1) {
					lastCPU -= 2*Math.abs(delta);
				} else if (lastCPU < 0) {
					lastCPU += 2*Math.abs(delta);
				}
				
				int d = (r.nextInt(4096) - 2048) / 10;
				lastRAM += d;
				if (lastRAM > 4096) {
					lastRAM -= 2*Math.abs(d);
				} else if (lastRAM < 0) {
					lastRAM += 2*Math.abs(d);
				}
				
				clk.plus_ms(500);
				graf.addValues(100 * lastCPU, lastRAM);
			}
		});
		
		graf.color(0, Color.BLUE.brighter());
		graf.color(1, Color.ORANGE);
		
		graf.unit(0, "%");
		graf.unit(1, "MiB");
		graf.range(0, 0, 100);
		graf.min(1, 0);
		
		graf.addValues(50f, 10f); // T=0.0: 50%, 10MiB
		clk.plus_ms(500); // T=0.5
		graf.addValues(35f, 15f);
		clk.plus_ms(500); // T=1.0
		graf.addValues(37f, 12f);
		clk.plus_ms(500); // T=1.5
		graf.addValues(33f, 7f);
		clk.plus_ms(500); // T=2.0
		graf.addValues(15f, 15f);
		clk.plus_ms(500); // T=2.5
		graf.addValues(1f, 12f);
		clk.plus_ms(500); // T=3.0
		graf.addValues(2f, Float.NaN); // Empty measurement in memory
		clk.plus_ms(500); // T=3.5
		graf.addValues(1.5f, 8f);
		clk.plus_ms(500); // T=4.0
		graf.addValues(1.2f, 5.5f);
		clk.plus_ms(500); // T=4.5
		graf.addValues(5f, 5f);
		clk.plus_ms(500); // T=5
		graf.addValues(5f, 5f);
		clk.plus_ms(500); // T=5.5
		graf.addValues(10f, 10f);
		
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.setSize(800, 600);
		f.setVisible(true);
	}
	
}
