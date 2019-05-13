package org.deidentifier.arx.algorithm;

import java.util.Arrays;

import org.deidentifier.arx.framework.check.TransformationChecker;
import org.deidentifier.arx.framework.check.history.History.StorageStrategy;
import org.deidentifier.arx.framework.lattice.SolutionSpace;

import de.linearbits.jhpl.PredictiveProperty;

/**
 * The Genetic Algorithm.
 * 
 * @author Kieu-Mi Do
 *
 */
public class GAAlgorithm extends AbstractAlgorithm {

	public static AbstractAlgorithm create(SolutionSpace solutionSpace, TransformationChecker checker) {
		return new GAAlgorithm(solutionSpace, checker);
	}

	private GAAlgorithmConfig config;
	private int[] maxValues;
	private final PredictiveProperty propertyChecked;

	public GAAlgorithm(SolutionSpace solutionSpace, TransformationChecker checker) {
		super(solutionSpace, checker);
		config = new GAAlgorithmConfig();
		this.checker.getHistory().setStorageStrategy(StorageStrategy.ALL);
		this.propertyChecked = solutionSpace.getPropertyChecked();
	}

	@Override
	public boolean traverse() {
		maxValues = solutionSpace.getTop().getGeneralization();
		System.out.println("Max: " + Arrays.toString(maxValues));

		int m = maxValues.length;
		int k = m + config.getSubpopulationSize();
		int itr = config.getIterations();
		int imm = config.getImmigrationInterval();
		int immf = config.getImmigrationFraction();
		float elitePercent = config.getElitePercent();
		int best = (int) Math.ceil(elitePercent * k);

		// Build Subpopulations
		GASubpopulation z1 = new GASubpopulation(m);
		GASubpopulation z2 = new GASubpopulation(m);

		// Fill Subpopulation 1
		for (int i = 0; i < k; i++) {
			int[] vec = new int[m];
			for (int j = 0; j < m; j++)
				vec[j] = Math.min(i < j ? 1 : 0, maxValues[j]);

			GAIndividual indi = new GAIndividual(solutionSpace, vec, propertyChecked, checker);
			trackOptimum(indi.getTransformation());
			z1.addIndividual(indi);

		}

		// Fill Subpopulation 2
		for (int i = 0; i < k; i++) {
			int[] vec = new int[m];
			for (int j = 0; j < m; j++)
				vec[j] = Math.min(Math.random() < 0.5 ? 1 : 0, maxValues[j]);

			GAIndividual indi = new GAIndividual(solutionSpace, vec, propertyChecked, checker);
			trackOptimum(indi.getTransformation());
			z2.addIndividual(indi);
		}

		// Main iterator
		for (int t = 0; t < itr; t++) {
			// Calculate the fitness and sort all individuals
			z1.sort();
			z2.sort();

			// Swap individuals between GASubpopulations periodically
			if (t % imm == 0) {
				z1.moveIndividuals(z2, immf);
				z2.moveIndividuals(z1, immf);
				z1.sort();
				z2.sort();
			}

			iterateSubpopulation(z1, best);
			iterateSubpopulation(z2, best);
		}

		return getGlobalOptimum() != null;
	}

	/**
	 * Performs one iteration on a subpopulation.
	 * 
	 * @param pop
	 * @param best
	 */
	private void iterateSubpopulation(GASubpopulation pop, int best) {
		// Calculate mutation config. parameters
		int k = pop.individualCount();
		int m = pop.colCount();

		float crossoverPercent = config.getCrossoverPercent();
		int crossoverCount = (int) Math.ceil(k * crossoverPercent);

		// Crossover a selection of individuals
		for (int crossover = 0; crossover < crossoverCount; crossover++) { // Select
																			// parents
			GAIndividual parent1 = selectRandomParent(pop, best);
			GAIndividual parent2 = selectRandomParent(pop, best);

			// Create crossover child
			GAIndividual child = pop.getIndividual(crossover + best);

			int[] vec = new int[m];
			for (int i = 0; i < m; i++)
				vec[i] = (Math.random() < 0.5 ? parent1 : parent2).getAsIntArray()[i];
			child.setTransform(vec);
		}

		// Mutate rest of individuals
		for (int mutation = best + crossoverCount; mutation < k; mutation++) {
			// Determine how the mutation should occur
			GAIndividual parent = selectRandomParent(pop, best);
			pop.removeIndividual(pop.getIndividual(mutation));

			GAIndividual indi = parent.mutate();
			trackOptimum(indi.getTransformation());
			pop.addIndividual(indi);
		}

	}

	/**
	 * Selects a random parent.
	 * 
	 * @param pop
	 * @param best
	 * @return
	 */
	private GAIndividual selectRandomParent(GASubpopulation pop, int best) {
		int r = (int) (Math.random() * best);
		return pop.getIndividual(r);
	}
}