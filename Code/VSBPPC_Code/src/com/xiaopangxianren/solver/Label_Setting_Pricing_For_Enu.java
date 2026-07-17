package com.xiaopangxianren.solver;

import com.xiaopangxianren.classes.BinType;
import com.xiaopangxianren.classes.Item;
import com.xiaopangxianren.classes.Parameter;
import com.xiaopangxianren.classes.Pattern;
import com.xiaopangxianren.utils.TimeUtil;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

public class Label_Setting_Pricing_For_Enu {
    final int LONG_SIZE = 64;
    public int n, curN, C;
    public Item[] items;
    private List<Pattern> patternList;

    private BinType bin;
    private double threshold;
    private int currentPatternsNum;
    private static int MAX_LABEL_NUM = 800000;
    private double[] suffixMaxDual;

    private Set<String> allPatternKeys;
    public boolean isOver = false;
    private double binTypeBoundDualVal;

    private BitSet[] conflictMatrix;
    private Integer[] idIndexMap;
    private boolean[] have;

    public Label_Setting_Pricing_For_Enu(int n, int curN, int currentPatternsNum, double UB, double LB,
                                         BinType bin, Item[] items, Set<String> allPatternKeys,
                                         double binTypeBoundDualVal, BitSet[] conflictMatrix,
                                         Integer[] idIndexMap, boolean[] have) {
        this.n = n;
        this.curN = curN;
        this.bin = bin;
        this.C = bin.capacity;
        this.items = items;
        this.threshold = UB - LB;
        this.allPatternKeys = allPatternKeys;
        this.patternList = new ArrayList<>();
        this.currentPatternsNum = currentPatternsNum;
        // 计算后缀和
        this.suffixMaxDual = new double[curN + 1];

        this.binTypeBoundDualVal = binTypeBoundDualVal;
        this.conflictMatrix = conflictMatrix;
        this.idIndexMap = idIndexMap;
        this.have = have;

        double sum = 0d;
        for (int i = curN - 1; i >= 0; i--) {
            sum += Math.max(0, items[i].dualVal);
            suffixMaxDual[i] = sum;
        }
    }

    static class LabelNode {
        public double reducedCost;
        public double minReducedCost;
        public int remainingCapacity;
        public BitSet itemSet;
        public long[] itemUnEnableLabel;
    }

    private void computeLocalBound(int a, LabelNode node) {
        // 直接贪心计算下界
        double minReducedCost = node.reducedCost;
        minReducedCost -= suffixMaxDual[a];
        node.minReducedCost = minReducedCost;
    }


    private List<LabelNode> removeNotExpendNode(int a, List<LabelNode> labelNodeList) {
        int size = labelNodeList.size();
        boolean[] delFlag = new boolean[size];
        List<LabelNode> newLabelSetNodeList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            computeLocalBound(a, labelNodeList.get(i));

            if (labelNodeList.get(i).minReducedCost > threshold) {
                delFlag[i] = true;
            }
        }
        for (int i = 0; i < size; i++) {
            if (!delFlag[i]) {
                newLabelSetNodeList.add(labelNodeList.get(i));
            }
        }
        return newLabelSetNodeList;
    }

    private void labelSetting() {
        List<LabelNode> currentLabelNodeList = new ArrayList<>();
        List<LabelNode> nextLabelNodeList = new ArrayList<>();

        // 初始化第一个节点（空物品集）
        LabelNode firstNode = new LabelNode();
        firstNode.remainingCapacity = C;
        firstNode.reducedCost = bin.cost - binTypeBoundDualVal;
        firstNode.itemSet = new BitSet(curN);
        firstNode.itemUnEnableLabel = new long[Math.max((curN + LONG_SIZE - 1) / LONG_SIZE, 1)];
        currentLabelNodeList.add(firstNode);

        int a = 0;
        while (a < curN && !TimeUtil.isTimeLimit()) {
            if (!currentLabelNodeList.isEmpty()) {
                currentLabelNodeList = removeNotExpendNode(a, currentLabelNodeList);

                Item item = items[a];

                int pos1_a = a / LONG_SIZE;
                int pos2_a = a % LONG_SIZE;
                long val_a = 1L << pos2_a;

                for (LabelNode labelNode : currentLabelNodeList) {
                    // 打包当前物品a
                    if (labelNode.remainingCapacity >= item.volume && (labelNode.itemUnEnableLabel[pos1_a] & val_a) == 0) {
                        LabelNode newLabelNode = new LabelNode();
                        newLabelNode.reducedCost = labelNode.reducedCost - item.dualVal;
                        newLabelNode.itemUnEnableLabel = labelNode.itemUnEnableLabel.clone();
                        newLabelNode.remainingCapacity = labelNode.remainingCapacity - item.volume;
                        newLabelNode.itemSet = (BitSet) labelNode.itemSet.clone();
                        newLabelNode.itemSet.set(a);

                        for (int i = conflictMatrix[item.id].nextSetBit(0); i >= 0; i = conflictMatrix[item.id].nextSetBit(i + 1)) {
                            if (have[i]) {
                                int ii = idIndexMap[i];
                                int pos1_j = ii / LONG_SIZE;
                                int pos2_j = ii % LONG_SIZE;
                                long val_j = 1L << pos2_j;
                                newLabelNode.itemUnEnableLabel[pos1_j] |= val_j;
                            }
                        }
                        nextLabelNodeList.add(newLabelNode);
                    }
                    // 不打包该物品
                    nextLabelNodeList.add(labelNode);

                    // 限制节点数量
                    if (nextLabelNodeList.size() >= MAX_LABEL_NUM) {
                        isOver = true;
                        break;
                    }
                }
                if (nextLabelNodeList.size() >= MAX_LABEL_NUM) {
                    System.out.println("Label Setting num exceeds limit: " + nextLabelNodeList.size());
                    return;
                }
                currentLabelNodeList = nextLabelNodeList;
                nextLabelNodeList = new ArrayList<>();
            }
            a++;
        }

        for (LabelNode labelNode : currentLabelNodeList) {
            if (labelNode.reducedCost < threshold && labelNode.itemSet.cardinality() > 0) {
                Pattern pattern = new Pattern();
                pattern.reducedCost = labelNode.reducedCost;
                pattern.placeItems = new ArrayList<>();
                BitSet bitSet = new BitSet(n);

                for (int i = labelNode.itemSet.nextSetBit(0); i >= 0; i = labelNode.itemSet.nextSetBit(i + 1)) {
                    Item item = items[i];
                    pattern.placeItems.add(item);
                    bitSet.set(item.id);
                }
                pattern.bitSet = bitSet;
                pattern.binType = bin;
                pattern.genKey();

                if (!allPatternKeys.contains(pattern.key)) {
                    allPatternKeys.add(pattern.key);
                    patternList.add(pattern);
                    currentPatternsNum++;
                    if (currentPatternsNum > Parameter.MAX_PATTERN_NUM) {
                        return;
                    }
                }
            }
        }
    }

    public List<Pattern> solve() {
        labelSetting();
        return patternList;
    }
}
