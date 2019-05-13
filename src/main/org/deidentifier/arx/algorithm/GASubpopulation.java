package org.deidentifier.arx.algorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a subpopulation.
 * 
 * @author Kieu-Mi Do
 *
 */
public class GASubpopulation {
	private List<GAIndividual> individuals = new ArrayList<>();
	private int colCount;

	/**
	 * Creates a new subpopulation.
	 * 
	 * @param colCount
	 */
	public GASubpopulation(int colCount) {
		this.colCount = colCount;
	}

	/**
	 * Adds an individual to the subpopulation.
	 * 
	 * @param ind
	 */
	public void addIndividual(GAIndividual ind) {
		individuals.add(ind);
	}

	/**
	 * Removes an individual from the subpopulation.
	 * 
	 * @param ind
	 */
	public void removeIndividual(GAIndividual ind) {
		individuals.remove(ind);
	}

	/**
	 * Gets the individual at index.
	 * 
	 * @param index
	 * @return
	 */
	public GAIndividual getIndividual(int index) {
		return individuals.get(index);
	}

	/**
	 * Moves 'count' Individuals from this subpopulation to 'other'
	 * 
	 * @param other
	 * @param count
	 */
	public void moveIndividuals(GASubpopulation other, int count) {
		int size = this.individualCount();
		int min = Math.min(count, size);
		for (int i = 0; i < min; i++)
			other.individuals.add(individuals.remove(0));
	}

	/**
	 * Gets the size of the subpopulation.
	 * 
	 * @return
	 */
	public int individualCount() {
		return individuals.size();
	}

	/**
	 * Gets the amount of columns.
	 * 
	 * @return
	 */
	public int colCount() {
		return colCount;
	}

	/**
	 * Sorts the Individuals according to their information loss.
	 */
	public void sort() {
		individuals.sort((a, b) -> {
			if (a == null)
				return -1;
			return a.getInformationLoss().compareTo(b.getInformationLoss());
		});
	}
}