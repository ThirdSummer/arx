/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2017 Fabian Prasser, Florian Kohlmayer and contributors
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
package org.deidentifier.arx.aggregates;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.mahout.math.Arrays;
import org.deidentifier.arx.ARXClassificationConfiguration;
import org.deidentifier.arx.ARXFeatureScaling;
import org.deidentifier.arx.ARXLogisticRegressionConfiguration;
import org.deidentifier.arx.ARXNaiveBayesConfiguration;
import org.deidentifier.arx.ARXRandomForestConfiguration;
import org.deidentifier.arx.ARXSVMConfiguration;
import org.deidentifier.arx.DataHandleInternal;
import org.deidentifier.arx.aggregates.classification.ClassificationDataSpecification;
import org.deidentifier.arx.aggregates.classification.ClassificationMethod;
import org.deidentifier.arx.aggregates.classification.ClassificationResult;
import org.deidentifier.arx.aggregates.classification.MultiClassLogisticRegression;
import org.deidentifier.arx.aggregates.classification.MultiClassNaiveBayes;
import org.deidentifier.arx.aggregates.classification.MultiClassRandomForest;
import org.deidentifier.arx.aggregates.classification.MultiClassSVM;
import org.deidentifier.arx.aggregates.classification.MultiClassZeroR;
import org.deidentifier.arx.common.WrappedBoolean;
import org.deidentifier.arx.common.WrappedInteger;
import org.deidentifier.arx.exceptions.ComputationInterruptedException;

import cern.colt.GenericSorting;
import cern.colt.Swapper;
import cern.colt.function.IntComparator;

/**
 * Statistics representing the prediction accuracy of a data mining
 * classification operator
 * 
 * @author Fabian Prasser
 */
public class StatisticsClassification {

    /**
     * A matrix mapping confidence thresholds to precision and recall
     * 
     * @author Fabian Prasser
     *
     */
    public static class PrecisionRecallMatrix {
        
        /** Measurements */
        private double                measurements          = 0d;
        /** Precision */
        private final double[]        precision             = new double[CONFIDENCE_THRESHOLDS.length];
        /** Recall */
        private final double[]        recall                = new double[CONFIDENCE_THRESHOLDS.length];
        /** F-Score */
        private final double[]        fscore                = new double[CONFIDENCE_THRESHOLDS.length];

        /**
         * Cut-off points
         * @return the confidence thresholds
         */
        public double[] getConfidenceThresholds() {
            return CONFIDENCE_THRESHOLDS;
        }
        
        /**
         * F-scores
         * @return the f-score
         */
        public double[] getFscore(){
            return fscore;
        }
        
        /**
         * Precision
         * @return the precision
         */
        public double[] getPrecision() {
            return precision;
        }

        /**
         * Recall
         * @return the recall
         */
        public double[] getRecall() {
            return recall;
        }

        /**
         * Adds a new value
         * @param confidence
         * @param correct
         */
        void add(double confidence, boolean correct) {
            
            for (int i = 0; i < CONFIDENCE_THRESHOLDS.length; i++) {
                if (confidence >= CONFIDENCE_THRESHOLDS[i]) {
                    recall[i]++;
                    precision[i] += correct ? 1d : 0d;
                }
            }
            measurements++;
        }

        /**
         * Packs the results
         */
        void pack() {
            // Pack
            for (int i = 0; i < CONFIDENCE_THRESHOLDS.length; i++) {
                
                if (recall[i] == 0d) {
                    precision[i] = 1d;
                } else {
                    precision[i] /= recall[i];
                    recall[i] /= measurements;
                }
                fscore[i] = 2 * (precision[i] * recall[i]) / (precision[i] + recall[i]);
            }
        }
    }

    /**
     * A ROC curve
     * 
     * @author Fabian Prasser
     *
     */
    public static class ROCCurve {

        /** Precision */
        private double[] truePositive  = new double[CONFIDENCE_THRESHOLDS.length];
        /** Recall */
        private double[] falsePositive = new double[CONFIDENCE_THRESHOLDS.length];
        /** Recall */
        private int[]    exists        = new int[CONFIDENCE_THRESHOLDS.length];
        /** AUC */
        private double   AUC           = 0d;

        /**
         * Returns the AUC
         * @return
         */
        public double getAUC() {
            return AUC;
        }

        /**
         * Returns false-positive rates for all cut-off points
         * @return the falsePositive
         */
        public double[] getFalsePositiveRate() {
            return falsePositive;
        }
        
        /**
         * Returns true-positive rates for all cut-off points
         * @return the truePositive
         */
        public double[] getTruePositiveRate() {
            return truePositive;
        }

        /**
         * Adds a new value
         * @param confidence
         * @param correct
         */
        void add(double confidence, boolean correct) {
            for (int i = 0; i < CONFIDENCE_THRESHOLDS.length; i++) {
                if (confidence >= CONFIDENCE_THRESHOLDS[i]) {
                    falsePositive[i] += correct ? 0d : 1d;
                    truePositive[i] += correct ? 1d : 0d;
                }
            }
        }
        
        /**
         * This value actually exists
         * @param confidence
         */
        void exists(double confidence) {
            for (int i = 0; i < CONFIDENCE_THRESHOLDS.length; i++) {
                if (confidence >= CONFIDENCE_THRESHOLDS[i]) {
                    exists[i] ++;
                }
            }
        }

        /**
         * Packs the results
         * @param classifications 
         */
        void pack(int[] classifications) {
            
            // Pack
            for (int i = 0; i < CONFIDENCE_THRESHOLDS.length; i++) {
                if (exists[i] != 0 && classifications[i] != 0) {
                    truePositive[i] /= (double)exists[i];
                    falsePositive[i] /= (double) (classifications[i] - exists[i]);
                }
            }

            // Sort
            GenericSorting.mergeSort(0, falsePositive.length, new IntComparator() {
                @Override
                public int compare(int arg0, int arg1) {
                    return Double.compare(falsePositive[arg0], falsePositive[arg1]);
                }
            }, new Swapper() {
                @Override
                public void swap(int arg0, int arg1) {
                    // Swap FP
                    double temp = falsePositive[arg0];
                    falsePositive[arg0] = falsePositive[arg1];
                    falsePositive[arg1] = temp;
                    // Swap TP
                    temp = truePositive[arg0];
                    truePositive[arg0] = truePositive[arg1];
                    truePositive[arg1] = temp;
                }
            });
            
            // Remove duplicates
            int shift = trim(truePositive, falsePositive);
            truePositive = Arrays.copyOf(truePositive, truePositive.length - shift);
            falsePositive = Arrays.copyOf(falsePositive, falsePositive.length - shift);
            
            // Make sane
            if (truePositive[0] != 0d && falsePositive[0] != 0d) {
                truePositive = insert(truePositive, 0, 0d);
                falsePositive = insert(falsePositive, 0, 0d);
            }
            if (truePositive[truePositive.length - 1] != 1d && falsePositive[falsePositive.length - 1] != 1d) {
                truePositive = insert(truePositive, truePositive.length, 1d);
                falsePositive = insert(falsePositive, falsePositive.length, 1d);
            }
            
            // Calculate AUC: trapezoidal rule
            for (int i=0; i<truePositive.length-1; i++) {
                double minX = Math.min(falsePositive[i], falsePositive[i + 1]);
                double maxX = Math.max(falsePositive[i], falsePositive[i + 1]);
                double minY = Math.min(truePositive[i], truePositive[i + 1]);
                double maxY = Math.max(truePositive[i], truePositive[i + 1]);
                AUC += (maxX - minX) * (minY + maxY) / 2d;
            }
        }

        /**
         * Removes duplicates
         * @param array1
         * @param array2
         * @return The number of elements removed
         */
        private int trim(double[] array1, double[] array2) {
            int trim = 0;
            int len = array1.length - 1;
            for (int i = 0; i < len; i++) {
                if (array1[i] == array1[i + 1] && array2[i] == array2[i + 1]) {
                    System.arraycopy(array1, i+1, array1, i, array1.length - i - 1);
                    System.arraycopy(array2, i+1, array2, i, array2.length - i - 1);
                    trim++; i--; len--;
                }
            }
            return trim;
        }

        /**
         * Inserts an element into the array
         * @param array
         * @param index
         * @param value
         * @return
         */
        private double[] insert(double[] array, int index, double value) {
            double[] result = new double[array.length + 1];
            for (int i = 0; i < result.length; i++) {
                if (i < index) {
                    result[i] = array[i];
                } else if (i == index) {
                    result[i] = value;
                } else {
                    result[i] = array[i - 1];
                }
            }
            return result;
        }
    }
    
    /** Confidence thresholds*/
    private static final double[] CONFIDENCE_THRESHOLDS = new double[]{
        0d, 0.1d, 0.2d, 0.3d, 0.4d, 0.5d, 0.6d, 0.7d, 0.8d, 0.9d, 1d
    };

    /** Accuracy */
    private double                accuracy;
    /** Average error */
    private double                averageError;
    /** Interrupt flag */
    private final WrappedBoolean  interrupt;
    /** Interrupt flag */
    private final WrappedInteger  progress;
    /** Precision/recall matrix */
    private PrecisionRecallMatrix matrix                = new PrecisionRecallMatrix();
    /** ROC curve */
    private Map<String, ROCCurve> ROC                   = new HashMap<>();
    /** Num classes */
    private int                   numClasses;
    /** Original accuracy */
    private double                originalAccuracy;
    /** Original accuracy */
    private double                originalAverageError;
    /** Precision/recall matrix */
    private PrecisionRecallMatrix originalMatrix        = new PrecisionRecallMatrix();
    /** ROC curve */
    private Map<String, ROCCurve> originalROC           = new HashMap<>();
    /** Random */
    private final Random          random;
    /** ZeroR accuracy */
    private double                zeroRAccuracy;
    /** ZeroR accuracy */
    private double                zeroRAverageError;
    /** Measurements */
    private int                   numMeasurements;

    /**
     * Creates a new set of statistics for the given classification task
     * @param inputHandle - The input features handle
     * @param outputHandle - The output features handle
     * @param features - The feature attributes
     * @param clazz - The class attributes
     * @param config - The configuration
     * @param scaling 
     * @param interrupt - The interrupt flag
     * @param progress 
     * @throws ParseException 
     */
    StatisticsClassification(DataHandleInternal inputHandle,
                             DataHandleInternal outputHandle,
                             String[] features,
                             String clazz,
                             ARXClassificationConfiguration config,
                             ARXFeatureScaling scaling, 
                             WrappedBoolean interrupt,
                             WrappedInteger progress) throws ParseException {

        // Init
        this.interrupt = interrupt;
        this.progress = progress;
        
        // Check and clean up
        double samplingFraction = (double)config.getMaxRecords() / (double)inputHandle.getNumRows();
        if (samplingFraction <= 0d) {
            throw new IllegalArgumentException("Sampling fraction must be >0");
        }
        if (samplingFraction > 1d) {
            samplingFraction = 1d;
        }
        
       
        // Initialize random
        if (!config.isDeterministic()) {
            this.random = new Random();
        } else {
            this.random = new Random(config.getSeed());
        }
        
        // Create specification
        ClassificationDataSpecification specification = new ClassificationDataSpecification(inputHandle, 
                                                                                            outputHandle, 
                                                                                            scaling,
                                                                                            features,
                                                                                            clazz,
                                                                                            interrupt);
        
        // Initialize ROC curves
        for (String classValue : specification.classMap.keySet()) {
            ROC.put(classValue, new ROCCurve());
            originalROC.put(classValue, new ROCCurve());
        }
        
        // Train and evaluate
        int k = inputHandle.getNumRows() > config.getNumFolds() ? config.getNumFolds() : inputHandle.getNumRows();
        List<List<Integer>> folds = getFolds(inputHandle.getNumRows(), k);

        // Track
        int[] classificationsOriginal = new int[CONFIDENCE_THRESHOLDS.length];
        int[] classificationsOutput = new int[CONFIDENCE_THRESHOLDS.length];
        int classifications = 0;
        double total = 100d / ((double)inputHandle.getNumRows() * (double)folds.size());
        double done = 0d;
                
        // For each fold as a validation set
        for (int evaluationFold = 0; evaluationFold < folds.size(); evaluationFold++) {
            
            // Create classifiers
            ClassificationMethod inputLR = getClassifier(specification, config);
            ClassificationMethod inputZR = new MultiClassZeroR(specification);
            ClassificationMethod outputLR = null;
            if (inputHandle != outputHandle) {
                outputLR = getClassifier(specification, config);
            }
            
            // Try
            try {
                
                // Train with all training sets
                boolean trained = false;
                for (int trainingFold = 0; trainingFold < folds.size(); trainingFold++) {
                    if (trainingFold != evaluationFold) {                        
                        for (int index : folds.get(trainingFold)) {
                            checkInterrupt();
                            inputLR.train(inputHandle, outputHandle, index);
                            inputZR.train(inputHandle, outputHandle, index);
                            if (outputLR != null && !outputHandle.isOutlier(index)) {
                                outputLR.train(outputHandle, outputHandle, index);
                            }
                            trained = true;
                            this.progress.value = (int)((++done) * total);
                        }
                    }
                }
                
                // Close
                inputLR.close();
                inputZR.close();
                if (outputLR != null) {
                    outputLR.close();
                }
                
                // Now validate
                for (int index : folds.get(evaluationFold)) {
                    
                    // Check
                    checkInterrupt();
                    
                    // If trained
                    if (trained) {
                        
                        // Classify
                        ClassificationResult resultInputLR = inputLR.classify(inputHandle, index);
                        ClassificationResult resultInputZR = inputZR.classify(inputHandle, index);
                        ClassificationResult resultOutputLR = outputLR == null ? null : outputLR.classify(outputHandle, index);
                        classifications++;
                        
                        // Correct result
                        String actualValue = outputHandle.getValue(index, specification.classIndex, true);
                        
                        // Maintain data about inputZR
                        this.zeroRAverageError += resultInputZR.error(actualValue);
                        this.zeroRAccuracy += resultInputZR.correct(actualValue) ? 1d : 0d;

                        // Maintain data about inputLR
                        boolean correct = resultInputLR.correct(actualValue);
                        this.originalAverageError += resultInputLR.error(actualValue);
                        this.originalAccuracy += correct ? 1d : 0d;
                        this.originalMatrix.add(resultInputLR.confidence(), correct);
                        this.originalROC.get(actualValue).exists(resultInputLR.confidence());
                        this.originalROC.get(specification.classMapInverse.get(resultInputLR.index())).add(resultInputLR.confidence(), correct);
                        
                        // Count
                        for (int i = 0; i < CONFIDENCE_THRESHOLDS.length; i++) {
                            if (resultInputLR.confidence() >= CONFIDENCE_THRESHOLDS[i]) {
                                classificationsOriginal[i] ++;
                            }
                        }
                        
                        // Maintain data about outputLR                        
                        if (resultOutputLR != null) {
                            correct = resultOutputLR.correct(actualValue);
                            this.averageError += resultOutputLR.error(actualValue);
                            this.accuracy += correct ? 1d : 0d;
                            this.matrix.add(resultOutputLR.confidence(), correct);
                            this.ROC.get(actualValue).exists(resultOutputLR.confidence());
                            this.ROC.get(specification.classMapInverse.get(resultOutputLR.index())).add(resultOutputLR.confidence(), correct);

                            // Count
                            for (int i = 0; i < CONFIDENCE_THRESHOLDS.length; i++) {
                                if (resultOutputLR.confidence() >= CONFIDENCE_THRESHOLDS[i]) {
                                    classificationsOutput[i] ++;
                                }
                            }
                            
                        }
                    }
                    this.progress.value = (int)((++done) * total);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        // Maintain data about inputZR
        this.zeroRAverageError /= (double)classifications;
        this.zeroRAccuracy/= (double)classifications;

        // Maintain data about inputLR
        this.originalAverageError /= (double)classifications;
        this.originalAccuracy /= (double)classifications;
        this.originalMatrix.pack();
        
        // ROCCurves
        for (ROCCurve curve : this.ROC.values()) {
            curve.pack(classificationsOutput);
        }
        // ROCCurves
        for (ROCCurve curve : this.originalROC.values()) {
            curve.pack(classificationsOriginal);
        }

        // Maintain data about outputLR                        
        if (inputHandle != outputHandle) {
            this.averageError /= (double)classifications;
            this.accuracy /= (double)classifications;
            this.matrix.pack();
        } else {
            this.averageError = this.originalAverageError;
            this.accuracy = this.originalAccuracy;
            this.matrix = this.originalMatrix;
        }
        
        this.numClasses = specification.classMap.size();
        this.numMeasurements = classifications;
    }

    /**
     * Returns the resulting accuracy. Obtained by training a
     * Logistic Regression classifier on the output (or input) dataset.
     * 
     * @return
     */
    public double getAccuracy() {
        return this.accuracy;
    }

    /**
     * Returns the average error, defined as avg(1d-probability-of-correct-result) for
     * each classification event.
     * 
     * @return
     */
    public double getAverageError() {
        return this.averageError;
    }
    
    /**
     * Returns the set of class attributes
     * @return
     */
    public Set<String> getClassValues() {
        return this.ROC.keySet();
    }

    /**
     * Returns the number of classes
     * @return
     */
    public int getNumClasses() {
        return this.numClasses;
    }
    
    /**
     * Returns the number of measurements
     * @return
     */
    public int getNumMeasurements() {
        return this.numMeasurements;
    }
    
    /**
     * Returns the maximal accuracy. Obtained by training a
     * Logistic Regression classifier on the input dataset.
     * 
     * @return
     */
    public double getOriginalAccuracy() {
        return this.originalAccuracy;
    }

    /**
     * Returns the average error, defined as avg(1d-probability-of-correct-result) for
     * each classification event.
     * 
     * @return
     */
    public double getOriginalAverageError() {
        return this.originalAverageError;
    }

    /**
     * Returns a precision/recall matrix for LogisticRegression on input
     * @return
     */
    public PrecisionRecallMatrix getOriginalPrecisionRecall() {
        return this.originalMatrix;
    }
    
    /**
     * Returns the ROC curve for this class value calculated using the one-vs-all approach.
     * @param clazz
     * @return
     */
    public ROCCurve getOriginalROCCurve(String clazz) {
        return this.originalROC.get(clazz);
    }
    
    /**
     * Returns a precision/recall matrix
     * @return
     */
    public PrecisionRecallMatrix getPrecisionRecall() {
        return this.matrix;
    }

    /**
     * Returns the ROC curve for this class value calculated using the one-vs-all approach.
     * @param clazz
     * @return
     */
    public ROCCurve getROCCurve(String clazz) {
        return this.ROC.get(clazz);
    }
    
    /**
     * Returns the minimal accuracy. Obtained by training a
     * ZeroR classifier on the input dataset.
     * 
     * @return
     */
    public double getZeroRAccuracy() {
        return this.zeroRAccuracy;
    }
    
    /**
     * Returns the average error, defined as avg(1d-probability-of-correct-result) for
     * each classification event.
     * 
     * @return
     */
    public double getZeroRAverageError() {
        return this.zeroRAverageError;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("StatisticsClassification{\n");
        builder.append(" - Accuracy:\n");
        builder.append("   * Original: ").append(originalAccuracy).append("\n");
        builder.append("   * ZeroR: ").append(zeroRAccuracy).append("\n");
        builder.append("   * Output: ").append(accuracy).append("\n");
        builder.append(" - Average error:\n");
        builder.append("   * Original: ").append(originalAverageError).append("\n");
        builder.append("   * ZeroR: ").append(zeroRAverageError).append("\n");
        builder.append("   * Output: ").append(averageError).append("\n");
        builder.append(" - Number of classes: ").append(numClasses).append("\n");
        builder.append(" - Number of measurements: ").append(numMeasurements).append("\n");
        builder.append("}");
        return builder.toString();
    }

    /**
     * Checks whether an interruption happened.
     */
    private void checkInterrupt() {
        if (interrupt.value) {
            throw new ComputationInterruptedException("Interrupted");
        }
    }
    
    /**
     * Returns the classification method for the given config
     * @param specification
     * @param config
     * @return
     */
    private ClassificationMethod getClassifier(ClassificationDataSpecification specification,
                                               ARXClassificationConfiguration config) {
        if (config instanceof ARXLogisticRegressionConfiguration) {
            return new MultiClassLogisticRegression(specification, (ARXLogisticRegressionConfiguration)config);
        } else if (config instanceof ARXNaiveBayesConfiguration) {
            System.setProperty("smile.threads", "1");
            return new MultiClassNaiveBayes(specification, (ARXNaiveBayesConfiguration)config);
        } else if (config instanceof ARXSVMConfiguration) {
            System.setProperty("smile.threads", "1");
            return new MultiClassSVM(specification, (ARXSVMConfiguration)config);
        } else if (config instanceof ARXRandomForestConfiguration) {
            System.setProperty("smile.threads", "1");
            return new MultiClassRandomForest(specification, (ARXRandomForestConfiguration)config);
        } else {
            throw new IllegalArgumentException("Unknown type of configuration");
        }
    }

    /**
     * Creates the folds
     * @param length
     * @param k
     * @param random
     * @return
     */
    private List<List<Integer>> getFolds(int length, int k) {
        
        // Prepare indexes
        List<Integer> rows = new ArrayList<>();
        for (int row = 0; row < length; row++) {
            rows.add(row);
        }
        Collections.shuffle(rows, random);
        
        // Create folds
        List<List<Integer>> folds = new ArrayList<>();
        int size = rows.size() / k;
        size = size > 1 ? size : 1;
        for (int i = 0; i < k; i++) {
            // For each fold
            int min = i * size;
            int max = (i + 1) * size;
            if (i == k - 1) {
                max = rows.size();
            }

            // Collect rows
            List<Integer> fold = new ArrayList<>();
            for (int j = min; j < max; j++) {
                fold.add(rows.get(j));
            }

            // Store
            folds.add(fold);
        }

        // Free
        rows.clear();
        rows = null;
        return folds;
    }
}
