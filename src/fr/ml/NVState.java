package fr.ml;

public class NVState {
	
	long timestamp = System.currentTimeMillis();
	
	int memUsed, memTotal;
	String memUnit;
	
	float cpu;
	
	int temp, tempSlow, tempStop;
	
	float power;
	String powerUnit;
	
	@Override
	public String toString() {
		return "CPU: "+Math.round(100*cpu)+"%, memory "+memUsed+" "+memUnit+" ("+Math.round(100f * memUsed / memTotal)+"%), temperature "+temp+"°C/"+tempSlow+"/"+tempStop+", power "+power+powerUnit;
	}
	
}