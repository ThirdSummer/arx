/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2018 Fabian Prasser and contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.deidentifier.arx.algorithm;

import java.util.Random;

import org.deidentifier.arx.framework.check.TransformationChecker;
import org.deidentifier.arx.framework.check.TransformationChecker.ScoreType;
import org.deidentifier.arx.framework.check.history.History.StorageStrategy;
import org.deidentifier.arx.framework.lattice.SolutionSpace;
import org.deidentifier.arx.framework.lattice.Transformation;

import cern.colt.list.LongArrayList;

/**
 * The genetic algorithm.
 * 
 * @author Kieu-Mi Do
 */
public class GAAlgorithm extends AbstractAlgorithm {

	/**
	 * Returns a new instance
	 * @param solutionSpace
	 * @param checker
	 * @return
	 */
	public static AbstractAlgorithm create(SolutionSpace solutionSpace, TransformationChecker checker) {
		return new GAAlgorithm(solutionSpace, checker);
	}

	/** RNG */
	private Random random;
	/** Configuration */
	private GAAlgorithmConfig config;
	/** Max values */
	private int[] maxValues;
	/** Min values */
	private int[] minValues;
	/** Checker */
	private TransformationChecker checker;

	/**
	 * Creates a new instance
	 * @param solutionSpace
	 * @param checker
	 */
	private GAAlgorithm(SolutionSpace solutionSpace, TransformationChecker checker) {
		super(solutionSpace, checker);
		this.config = new GAAlgorithmConfig();
		this.checker = checker;
		this.checker.getHistory().setStorageStrategy(StorageStrategy.ALL);
		this.maxValues = solutionSpace.getTop().getGeneralization();
		this.minValues = solutionSpace.getBottom().getGeneralization();
		this.random = this.config.isDeterministic() ? new Random(0xDEADBEEF) : new Random();
	}

	@Override
	public boolean traverse() {

		// Prepare
		int k = this.maxValues.length + config.getSubpopulationSize(); // TODO: Why is k defined this way? Please explain and document?
		int itr = config.getIterations();
		int imm = config.getImmigrationInterval();
		int immf = config.getImmigrationFraction();
		double elitePercent = config.getElitePercent();
		int best = (int) Math.ceil(elitePercent * k);

		// Build sub-populations
		GASubpopulation z1 = new GASubpopulation();
		GASubpopulation z2 = new GASubpopulation();

		// Fill sub-population 1
		for (int i = 0; i < k; i++) {
			
			int[] vec = new int[maxValues.length];
			
			// TODO: I didn't understand the logic that was implemented here
			// TODO: And just replaced it with random choices
			for (int j = 0; j < maxValues.length; j++) {
				vec[j] = minValues[j] + (int)(random.nextDouble() * (maxValues[j] - minValues[j])); 
			}
			z1.addIndividual(getIndividual(vec));
		}

		// Fill sub-population 2
		for (int i = 0; i < k; i++) {
			
			int[] vec = new int[maxValues.length];
			
			// TODO: I didn't understand the logic that was implemented here
			// TODO: And just replaced it with random choices
			for (int j = 0; j < maxValues.length; j++) {
				vec[j] = minValues[j] + (int)(random.nextDouble() * (maxValues[j] - minValues[j])); 
			}
			z2.addIndividual(getIndividual(vec));
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

		// Check whether we found a solution
		return getGlobalOptimum() != null;
	}
	
	/**
	 * Returns an individual
	 * @param generalization
	 * @return
	 */
	private Transformation getIndividual(int[] generalization) {
		Transformation transformation = this.solutionSpace.getTransformation(generalization);
		if (!transformation.hasProperty(this.solutionSpace.getPropertyChecked())) {
			transformation.setChecked(this.checker.check(transformation, true, ScoreType.INFORMATION_LOSS));
		}
		trackOptimum(transformation);
		return transformation;
	}
	
	/**
	 * Returns a mutated transformation
	 * 
	 * @return
	 */
	private Transformation getMutatedIndividual(Transformation transformation) {
		LongArrayList list = transformation.getSuccessors();
		int r = (int) (this.random.nextDouble() * list.size());
		long s = list.getQuick(r);
		return getIndividual(this.solutionSpace.getTransformation(s).getGeneralization());
	}
	
	/**
	 * Selects a random parent.
	 * 
	 * @param pop
	 * @param best
	 * @return
	 */
	private Transformation getRandomParent(GASubpopulation pop, int best) {
		int r = (int) (random.nextDouble() * best);
		return pop.getIndividual(r);
	}
	
	/**
	 * Performs one iteration on a subpopulation.
	 * 
	 * @param pop - TODO: What is this?
	 * @param best
	 */
	private void iterateSubpopulation(GASubpopulation pop, int best) {
		
		// Calculate mutation configuration parameters
		int k = pop.individualCount();
		double crossoverPercent = config.getCrossoverPercent();
		int crossoverCount = (int) Math.ceil(k * crossoverPercent);
		
		// Crossover a selection of individuals
		for (int crossover = 0; crossover < crossoverCount; crossover++) {
			
			// Select parents
			Transformation parent1 = getRandomParent(pop, best);
			Transformation parent2 = getRandomParent(pop, best);

			// Create crossover child
			int[] vec = new int[maxValues.length];
			for (int i = 0; i < maxValues.length; i++) {
				vec[i] = (random.nextDouble() < 0.5 ? parent1 : parent2).getGeneralization()[i];
			}
			
			// Replace
			pop.setIndividual(crossover + best, getIndividual(vec));
		}

		// TODO: I don't understand the parameters in the for-loop. Please explain and document.
		// Mutate rest of individuals
		for (int mutation = best + crossoverCount; mutation < k; mutation++) {
			
			// Determine how the mutation should occur
			Transformation parent = getRandomParent(pop, best);
			pop.removeIndividual(mutation);
			Transformation individual = getMutatedIndividual(parent);
			trackOptimum(individual);
			pop.addIndividual(individual);
		}
	}
}