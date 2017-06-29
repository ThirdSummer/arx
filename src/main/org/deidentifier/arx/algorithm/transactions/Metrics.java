package org.deidentifier.arx.algorithm.transactions;

public class Metrics {

    // Information Loss (IL) if item is generalized by g
    public static double NCP(int item, int g, GenHierarchy h, int domainsize) {
        double leaveCount = 0;
        boolean getsGeneralized = false;

        for (int i = 0; i < h.toRoot(item).length; i++) {
            if (h.toRoot(item)[i] == g)
                getsGeneralized = true;
        }
        if (!getsGeneralized)
            return 0;

        for (int i = 0; i < domainsize; i++) {
            for (int j = 0; j < h.toRoot(i).length; j++) {
                if (h.toRoot(i)[j] == g) {
                    leaveCount++;
                    break;
                }
            }
        }
        if (leaveCount == 1)
            return 0;
        else
            return leaveCount / domainsize;
    }

    /**
     * @param generalization the generalization for which the information loss is computed
     * @param tran           the database
     * @param h              the generalization hierarchy which generalization belongs to
     * @param domainsize     the domainsize i.e. the count of non generalizations in h
     * @param ct             the count-tree for tran
     * @return the information loss inflicted by generalization on database tran
     */
    public static double NCP(int generalization, int[][] tran, GenHierarchy h, int domainsize,
                             CountTree ct) {
        int dbsize = 0;
        int[] occurences = ct.itemFrequencies();

        for (int[] aTran : tran) {
            dbsize += aTran.length;
        }
        double sum = 0;

        for (int i = 0; i < occurences.length; i++) {
            sum += occurences[i] * NCP(i, generalization, h, domainsize);
        }
        return sum / dbsize;
    }

    /**
     * @param g          the cut for which the information loss is computed
     * @param tran       the database
     * @param h          the generalization hierarchy which g belongs to
     * @param domainsize the domainsize i.e. the count of non generalizations in h
     * @param ct         the count-tree for tran
     * @return the information loss inflicted by g on database tran
     */
    public static double NCP(Cut g, int[][] tran, GenHierarchy h, int domainsize, CountTree ct) {
        double sum = 0;
        for (int i = 0; i < g.generalization.length; i++) {
            sum += NCP(g.getGeneralization(i), tran, h, domainsize, ct);
        }
        return sum;
    }
}
