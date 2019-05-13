package org.deidentifier.arx.algorithm;

import org.deidentifier.arx.framework.check.TransformationChecker;
import org.deidentifier.arx.framework.check.TransformationChecker.ScoreType;
import org.deidentifier.arx.framework.lattice.SolutionSpace;
import org.deidentifier.arx.framework.lattice.Transformation;
import org.deidentifier.arx.metric.InformationLoss;

import cern.colt.list.LongArrayList;
import de.linearbits.jhpl.PredictiveProperty;

/**
 * Represents a potential transformation within the solution space.
 * 
 * @author Kieu-Mi Do
 *
 */
public class GAIndividual {
	private SolutionSpace solutionSpace;
	private Transformation transform;
	private InformationLoss<?> informationLoss;
	private int[] vec;
	private PredictiveProperty propertyChecked;
	TransformationChecker checker;

	/**
	 * Creates a new individual based on a given 'Transformation'.
	 * 
	 * @param solutionSpace
	 * @param vec
	 *            - int array representing the lattice vector.
	 * @param propertyChecked
	 * @param checker
	 */
	public GAIndividual(SolutionSpace solutionSpace, int[] vec, PredictiveProperty propertyChecked,
			TransformationChecker checker) {
		this.solutionSpace = solutionSpace;
		this.vec = vec;
		this.propertyChecked = propertyChecked;
		this.checker = checker;

		setTransform(vec);
	}

	/**
	 * Creates a new individual based on a given Transformation.
	 * 
	 * @param solutionSpace
	 * @param transform
	 * @param propertyChecked
	 * @param checker
	 */
	public GAIndividual(SolutionSpace solutionSpace, Transformation transform, PredictiveProperty propertyChecked,
			TransformationChecker checker) {
		this.solutionSpace = solutionSpace;
		this.transform = transform;
		this.propertyChecked = propertyChecked;
		this.checker = checker;

		if (!transform.hasProperty(propertyChecked)) {
			transform.setChecked(checker.check(transform, true, ScoreType.INFORMATION_LOSS));
		}
		informationLoss = transform.getInformationLoss();
	}

	/**
	 * Gets the information loss.
	 * 
	 * @return
	 */
	public InformationLoss<?> getInformationLoss() {
		return informationLoss;
	}

	/**
	 * Gets the Transformation.
	 * 
	 * @return
	 */
	public Transformation getTransformation() {
		return transform;
	}

	/**
	 * Converts Tranformation to int array representing the lattice-vector.
	 * 
	 * @return
	 */
	public int[] getAsIntArray() {
		if (vec == null) {
			vec = transform.getGeneralization();
		}
		return vec;
	}

	/**
	 * sets the Transformation.
	 * 
	 * @param vec
	 */
	public void setTransform(int[] vec) {
		this.vec = vec;
		transform = solutionSpace.getTransformation(vec);

		if (!transform.hasProperty(propertyChecked)) {
			transform.setChecked(checker.check(transform, true, ScoreType.INFORMATION_LOSS));
		}
		informationLoss = transform.getInformationLoss();
	}

	/**
	 * Returns a copy of the Transformation with a mutation.
	 * 
	 * @return
	 */
	public GAIndividual mutate() {
		LongArrayList list = transform.getSuccessors();
		int r = (int) (Math.random() * list.size());
		long s = list.getQuick(r);
		return new GAIndividual(solutionSpace, solutionSpace.getTransformation(s), propertyChecked, checker);
	}
}
