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
 * TODO: Which algorithm? Please include a reference.
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
		int k = this.maxValues.length + config.getSubpopulationSize(); // TODO: Why is k defined this way? Please explain and document.
		int itr = config.getIterations();
		int imm = config.getImmigrationInterval();
		int immf = config.getImmigrationFraction();

		// Build sub-populations
		GASubpopulation z1 = new GASubpopulation();
		GASubpopulation z2 = new GASubpopulation();

		// Fill sub-population 1
		for (int i = 0; i < k; i++) {
			
			int[] vec = new int[maxValues.length];
			
			// TODO: I didn't understand the logic that was implemented here
			// TODO: And just replaced it with random choices
			// TODO: Maybe this is specified differently and needs to be changed and documented
			// TODO: Triangle matrix?
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
			// TODO: Maybe this is specified differently and needs to be changed and documented
			for (int j = 0; j < maxValues.length; j++) {
				vec[j] = minValues[j] + (int)(random.nextDouble() * (maxValues[j] - minValues[j])); 
			}
			z2.addIndividual(getIndividual(vec));
		}

		// Main iterator
		for (int t = 0; t < itr; t++) {
			
			// Sort by fitness descending
			z1.sort();
			z2.sort();

			// Swap individuals between GASubpopulations periodically
			if (t % imm == 0) {
				
				// Moves the imff fittest individuals between groups
				z1.moveFittestIndividuals(z2, immf);
				z2.moveFittestIndividuals(z1, immf);
				
				// Sort by fitness descending
				z1.sort();
				z2.sort();
			}

			// Iterate
			iterateSubpopulation(z1);
			iterateSubpopulation(z2);
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
	 * Returns a mutated transformation, which means that a random parent is selected.
	 * TODO: Is this how it is specified? Please document.
	 * 
	 * @return
	 */
	private Transformation getMutatedIndividual(Transformation transformation) {
		LongArrayList list = transformation.getSuccessors();
		if (list == null || list.isEmpty()) {
			return null;
		}
		int r = (int) (this.random.nextDouble() * list.size());
		long s = list.getQuick(r);
		return getIndividual(this.solutionSpace.getTransformation(s).getGeneralization());
	}
	
	/**
	 * Selects a random individual within the given range from [0, range[.
	 * 
	 * @param pop
	 * @param range
	 * @return
	 */
	private Transformation getRandomIndividual(GASubpopulation pop, int range) {
		int r = (int) (random.nextDouble() * range);
		return pop.getIndividual(r);
	}
	
	/**
	 * Performs one iteration on a subpopulation.
	 * 
	 * @param pop - TODO: What is this?
	 */
	private void iterateSubpopulation(GASubpopulation pop) {
		
		// The population (ordered by fitness descending) consists of 3 groups
		// First: all individuals in the elite group will remain unchanged
		// Second: all individuals in the next group will be crossed over
		// Third: all remaining individuals will be mutated
		
		// Calculate mutation configuration parameters
		int k = pop.individualCount();
		int crossoverCount = (int) Math.ceil(config.getCrossoverPercent() * k);
		int eliteCount = (int) Math.ceil(config.getElitePercent() * k);
		
		// Crossover a selection of individuals
		for (int crossover = 0; crossover < crossoverCount; crossover++) {
			
			// Select parents from elite group
			Transformation parent1 = getRandomIndividual(pop, eliteCount);
			Transformation parent2 = getRandomIndividual(pop, eliteCount);

			// Create crossover child
			int[] vec = new int[maxValues.length];
			for (int i = 0; i < maxValues.length; i++) {
				vec[i] = (random.nextDouble() < 0.5 ? parent1 : parent2).getGeneralization()[i];
			}
			
			// Replace
			// TODO: This replaces the best individuals of the non-elite group
			// TODO: Wouldn't it be better to replace the worst individuals in the non-elite group
			pop.setIndividual(eliteCount + crossover, getIndividual(vec));
		}

		// Mutate rest of individuals
		for (int mutation = eliteCount + crossoverCount; mutation < k; mutation++) {
			
			// Select random parent from elite group
			Transformation parent = getRandomIndividual(pop, eliteCount);
			Transformation individual = getMutatedIndividual(parent);
			if (individual != null) {
				pop.setIndividual(mutation, individual);
			}
		}
	}
}