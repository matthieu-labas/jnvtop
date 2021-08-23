package fr.ml;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.Function;

// record TitleValUnit<T>(String title, T value, String unit) { } would be nice if my Eclipse understood it...

class TitleValUnit<T> {
	
	String title;
	T value;
	String unit;
	
	TitleValUnit(String title, T value, String unit) {
		this.title = title;
		this.value = value;
		this.unit = unit;
	}
	
	@Override
	public String toString() {
		return title+": "+value+" "+unit;
	}
	
	static private <T> TitleValUnit<T> read(BufferedReader rd, Function<String,T> reader) throws IOException {
		String line = rd.readLine();
		int colon = line.indexOf(':');
		String title = line.substring(0, colon).trim();
		line = line.substring(colon+2);
		int sep = line.indexOf(' ');
		String unit;
		T val;
		if (sep < 0) {
			unit = "";
			val = reader.apply(line);
		} else {
			unit = line.substring(sep+1).trim();
			val = reader.apply(line.substring(0, sep));
		}
		return new TitleValUnit<>(title, val, unit);
	}
	
	static public TitleValUnit<Float> readFloat(BufferedReader rd) throws IOException {
		return read(rd, TitleValUnit::readFloat);
	}
	static public TitleValUnit<Integer> readInteger(BufferedReader rd) throws IOException {
		return read(rd, TitleValUnit::readInt);
	}
	
	static private Float readFloat(String line) {
		try {
			return Float.valueOf(line);
		} catch (NumberFormatException e) {
			return null; // Probably "N/A" // TODO: Float.NaN ?
		}
	}
	static private Integer readInt(String line) {
		try {
			return Integer.valueOf(line);
		} catch (NumberFormatException e) {
			return null; // Probably "N/A"
		}
	}
	
}