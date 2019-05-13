package org.deidentifier.arx.algorithm;

/**
 * Configuration properties for how the GA should alter the data.
 * 
 * @author Kieu-Mi Do
 *
 */
public class GAAlgorithmConfig {

	private int subpopulationSize = 100;
	private int iterations = 50;
	private int immigrationInterval = 10;
	private int immigrationFraction = 10;
	private float elitePercent = 0.2f;
	private float crossoverPercent = 0.2f;

	public int getSubpopulationSize() {
		return subpopulationSize;
	}

	public int getIterations() {
		return iterations;
	}

	public int getImmigrationInterval() {
		return immigrationInterval;
	}

	public int getImmigrationFraction() {
		return immigrationFraction;
	}

	public float getElitePercent() {
		return elitePercent;
	}

	public float getCrossoverPercent() {
		return crossoverPercent;
	}
}
