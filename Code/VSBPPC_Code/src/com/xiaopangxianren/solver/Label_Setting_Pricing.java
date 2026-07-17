package com.xiaopangxianren.solver;

import com.xiaopangxianren.classes.BinType;
import com.xiaopangxianren.classes.DoubleValue_SR_Cut;
import com.xiaopangxianren.classes.Item;
import com.xiaopangxianren.classes.Pattern;

import java.util.*;

public class Label_Setting_Pricing {
    final int LONG_SIZE = 64;
    public double minReducedCost = 0d;
    int maxLabelCnt = 500_000;

    public int[][] eachItemSameIndexList;
    public int[] fatWeightArr;
    public BinType binType;
    private List<Pattern> patterns = new ArrayList<>();
    public int n, curN, C;
    public Item[] items;
    public int[] fat;
    private Set<String> allPatternKeys;

    public int srCutNum;
    public int[][] activatedSrConstraintIndexList;
    public DoubleValue_SR_Cut[] srCutList;

    private double[] dpArr;

    private double binTypeBoundDualVal;

    private Boolean[][] branchConstraintMatrix;
    private Integer[] idIndexMap;
    private boolean[] have;

    public Label_Setting_Pricing(int n, int curN, BinType binType, Item[] items, int[] fat,
                                 DoubleValue_SR_Cut[] srCutList, Set<String> allPatternKeys, double[] dpArr,
                                 double binTypeBoundDualVal, Boolean[][] branchConstraintMatrix, Integer[] idIndexMap, boolean[] have) {
        this.n = n;
        this.curN = curN;
        this.binType = binType;
        this.C = binType.capacity;
        this.items = items;

        this.fat = fat;
        this.allPatternKeys = allPatternKeys;

        this.srCutList = srCutList;
        this.srCutNum = srCutList.length;
        this.dpArr = dpArr;

        this.binTypeBoundDualVal = binTypeBoundDualVal;

        // 添加冲突矩阵
        this.branchConstraintMatrix = branchConstraintMatrix;
        this.idIndexMap = idIndexMap;
        this.have = have;
    }

    static class LabelNode implements Comparable<LabelNode> {
        public long[] itemUnEnableLabel;
        public long[] srCutLabel;
        public double reducedCost;
        public double minReducedCost;
        public int remainingCapacity;
        public List<Item> packedItemList;

        @Override
        public int compareTo(LabelNode o) {
            if (reducedCost < o.reducedCost) {
                return -1;
            } else if (reducedCost > o.reducedCost) {
                return 1;
            } else if (remainingCapacity < o.remainingCapacity) {
                return 1;
            } else if (remainingCapacity > o.remainingCapacity) {
                return -1;
            } else if (minReducedCost < o.minReducedCost) {
                return -1;
            } else if (minReducedCost > o.minReducedCost) {
                return 1;
            }
            return 0;
        }
    }

    private void init() {
        // 基础绑定（same图的最大连通分量）
        int[][] maximumConnectedComponents = new int[curN][];
        for (int i = 0; i < curN; i++) {
            List<Integer> list = new ArrayList<>(curN);
            for (int j = 0; j < curN; j++) if (fat[i] == fat[j]) list.add(j);
            int[] arr = new int[list.size()];
            int a = 0;
            for (int index : list) arr[a++] = index;
            maximumConnectedComponents[i] = arr;
        }

        // 计算放了i之后必须放的物品index
        eachItemSameIndexList = new int[curN][];
        for (int i = 0; i < curN; i++) {
            boolean[] set = new boolean[curN];
            List<Integer> list = new ArrayList<>();
            set[i] = true;
            list.add(i);
            while (true) {
                int size = list.size();
                // 添加list中索引index的绑定物品
                for (int index : new ArrayList<>(list)) {
                    for (int k : maximumConnectedComponents[index]) {
                        if (!set[k]) {
                            set[k] = true;
                            list.add(k);
                        }
                    }
                }
                if (list.size() == size) break;
            }
            int[] arr = new int[list.size()];
            int a = 0;
            for (int index : list) arr[a++] = index;
            eachItemSameIndexList[i] = arr;
        }

        fatWeightArr = new int[curN];
        for (int i = 0; i < curN; i++) {
            for (int j : maximumConnectedComponents[i]) fatWeightArr[i] += items[j].volume;
        }

        // SR cuts
        List<List<Integer>> temp_activatedSrConstraintIndexList = new ArrayList<>(curN);
        for (int i = 0; i < curN; i++) temp_activatedSrConstraintIndexList.add(new ArrayList<>());
        for (int r = 0; r < srCutNum; r++) {
            for (int index : srCutList[r].indexs) {
                if (index != -1) temp_activatedSrConstraintIndexList.get(index).add(r);
            }
        }
        activatedSrConstraintIndexList = new int[curN][];
        for (int i = 0; i < curN; i++) {
            List<Integer> list = temp_activatedSrConstraintIndexList.get(i);
            int[] arr = new int[list.size()];
            int a = 0;
            for (int index : list) arr[a++] = index;
            activatedSrConstraintIndexList[i] = arr;
        }
    }

    private void computeLocalBound(int a, LabelNode node) {
        int remainingCapacity = node.remainingCapacity;
        double minReducedCost = node.reducedCost;
        for (; a < curN; a++) {
            int pos1_a = a / LONG_SIZE;
            int pos2_a = a % LONG_SIZE;
            long val_a = 1L << pos2_a;
            if ((node.itemUnEnableLabel[pos1_a] & val_a) == 0) {
                Item item = items[a];
                if (item.volume <= remainingCapacity) {
                    minReducedCost -= item.dualVal;
                    remainingCapacity -= item.volume;
                } else {
                    minReducedCost -= item.unitVal * remainingCapacity;
                    break;
                }
            }
        }
        node.minReducedCost = Math.max(node.reducedCost - dpArr[node.remainingCapacity], minReducedCost);
    }

    private int cmpNodeDominate(int idx, LabelNode a, LabelNode b) {
        if (a.reducedCost <= b.minReducedCost) return 1;
        if (b.reducedCost <= a.minReducedCost) return -1;

        int remainingCapacityCmpAB = Integer.compare(a.remainingCapacity, b.remainingCapacity);
        int reducedCostCmpAB = Double.compare(a.reducedCost, b.reducedCost);

        if (remainingCapacityCmpAB >= 0 && reducedCostCmpAB <= 0) {
            double v = a.reducedCost - b.reducedCost;

            // srCut
            for (int r = 0; r < srCutNum; r++) {
                int pos1_r = r / LONG_SIZE;
                int pos2_r = r % LONG_SIZE;
                long val_r = 1L << pos2_r;
                if ((a.srCutLabel[pos1_r] & val_r) == val_r && (b.srCutLabel[pos1_r] & val_r) == 0)
                    v -= srCutList[r].dualValue;
            }

            for (int i = idx; i < curN; i++) {
                int pos1_i = i / LONG_SIZE;
                int pos2_i = i % LONG_SIZE;
                long val_i = 1L << pos2_i;
                if ((b.itemUnEnableLabel[pos1_i] & val_i) == 0 && (a.itemUnEnableLabel[pos1_i] & val_i) == val_i) {
                    if (items[i].dualVal > 0) {
                            v += items[i].dualVal;
                    }
                }
            }
            if (v <= 0) return 1;
        }

        if (remainingCapacityCmpAB <= 0 && reducedCostCmpAB >= 0) {
            double v = b.reducedCost - a.reducedCost;

            // srCut
            for (int r = 0; r < srCutNum; r++) {
                int pos1_r = r / LONG_SIZE;
                int pos2_r = r % LONG_SIZE;
                long val_r = 1L << pos2_r;
                if ((b.srCutLabel[pos1_r] & val_r) == val_r && (a.srCutLabel[pos1_r] & val_r) == 0)
                    v -= srCutList[r].dualValue;
            }

            for (int i = idx; i < curN; i++) {
                int pos1_i = i / LONG_SIZE;
                int pos2_i = i % LONG_SIZE;
                long val_i = 1L << pos2_i;
                if ((a.itemUnEnableLabel[pos1_i] & val_i) == 0 && (b.itemUnEnableLabel[pos1_i] & val_i) == val_i) {
                    if (items[i].dualVal > 0) {
                            v += items[i].dualVal;
                    }
                }
            }
            if (v <= 0) return -1;
        }
        return 0;
    }

    private List<LabelNode> removeDominatedNode(int a, List<LabelNode> labelNodeList) {
        int size = labelNodeList.size();
        Collections.sort(labelNodeList);
        boolean[] delFlag = new boolean[size];
        double minReducedCost = labelNodeList.getFirst().reducedCost;
        List<LabelNode> newLabelSetNodeList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            LabelNode labelNodeI = labelNodeList.get(i);
            if (!delFlag[i]) {
                if (labelNodeI.minReducedCost >= minReducedCost) {
                    delFlag[i] = true;
                    continue;
                }

                int j = i + 1;
                if (j < size && !delFlag[j]) {
                    int cmp = cmpNodeDominate(a, labelNodeI, labelNodeList.get(j));
                    if (cmp == -1) {
                        delFlag[i] = true;
                    } else if (cmp == 1) {
                        delFlag[j] = true;
                    }
                }
            }
            if (!delFlag[i]) {
                newLabelSetNodeList.add(labelNodeI);
                if (newLabelSetNodeList.size() == maxLabelCnt) {
                    throw new RuntimeException("number of labels is over");
                }
            }
        }
        return newLabelSetNodeList;
    }

    private void labelSetting() {
        List<LabelNode> currentLabelNodeList = new ArrayList<>();
        List<LabelNode> nextLabelNodeList = new ArrayList<>();

        LabelNode firstNode = new LabelNode();
        firstNode.remainingCapacity = C;
        firstNode.reducedCost = binType.cost - binTypeBoundDualVal;
        firstNode.itemUnEnableLabel = new long[Math.max((curN + LONG_SIZE - 1) / LONG_SIZE, 1)];
        firstNode.srCutLabel = new long[Math.max((srCutNum + LONG_SIZE - 1) / LONG_SIZE, 1)];
        firstNode.packedItemList = new ArrayList<>(curN);

        computeLocalBound(0, firstNode);
        currentLabelNodeList.add(firstNode);

        int a = 0;
        while (a < curN) {
            if (!currentLabelNodeList.isEmpty()) {
                currentLabelNodeList = removeDominatedNode(a, currentLabelNodeList);

                int pos1_a = a / LONG_SIZE;
                int pos2_a = a % LONG_SIZE;
                long val_a = 1L << pos2_a;

                for (LabelNode labelNode : currentLabelNodeList) {

                    // 打包
                    if (labelNode.remainingCapacity >= fatWeightArr[a]
                            && (labelNode.itemUnEnableLabel[pos1_a] & val_a) == 0) {
                        List<Item> newPackedItemList = new ArrayList<>(labelNode.packedItemList);
                        LabelNode newLabelNode = new LabelNode();
                        newLabelNode.itemUnEnableLabel = labelNode.itemUnEnableLabel.clone();
                        newLabelNode.srCutLabel = labelNode.srCutLabel.clone();
                        newLabelNode.reducedCost = labelNode.reducedCost;
                        newLabelNode.remainingCapacity = labelNode.remainingCapacity;
                        // 将与 idx 绑定的物品 i 全部打包
                        for (int i : eachItemSameIndexList[a]) {
                            int pos1_i = i / LONG_SIZE;
                            int pos2_i = i % LONG_SIZE;
                            long val_i = 1L << pos2_i;
                            if ((newLabelNode.itemUnEnableLabel[pos1_i] & val_i) == 0) {
                                Item itemI = items[i];
                                newLabelNode.remainingCapacity -= itemI.volume;
                                if (newLabelNode.remainingCapacity < 0) break;

                                // 更新 SR 不等式
                                for (int r : activatedSrConstraintIndexList[i]) {
                                    DoubleValue_SR_Cut srCut = srCutList[r];
                                    int pos1_r = r / LONG_SIZE;
                                    int pos2_r = r % LONG_SIZE;
                                    long val_r = 1L << pos2_r;
                                    if ((newLabelNode.srCutLabel[pos1_r] & val_r) == 0) {
                                        newLabelNode.srCutLabel[pos1_r] |= val_r;
                                    } else {
                                        newLabelNode.srCutLabel[pos1_r] &= (~val_r);
                                        newLabelNode.reducedCost -= srCut.dualValue;
                                    }
                                }

                                newPackedItemList.add(itemI);
                                newLabelNode.reducedCost -= itemI.dualVal;
                                newLabelNode.itemUnEnableLabel[pos1_i] |= val_i;

                                boolean[] isUsed = new boolean[curN];
                                for (int j = 0; j < n; j++) {
                                    if (branchConstraintMatrix[itemI.id][j] != null && !branchConstraintMatrix[itemI.id][j] && have[j]) {
                                        int jj = idIndexMap[j];
                                        int pos1_j = jj / LONG_SIZE;
                                        int pos2_j = jj % LONG_SIZE;
                                        long val_j = 1L << pos2_j;
                                        newLabelNode.itemUnEnableLabel[pos1_j] |= val_j;
                                        isUsed[jj] = true;
                                        for (int k : eachItemSameIndexList[jj]) {
                                            if (!isUsed[k]) {
                                                isUsed[k] = true;
                                                int pos1_k = k / LONG_SIZE;
                                                int pos2_k = k % LONG_SIZE;
                                                long val_k = 1L << pos2_k;
                                                newLabelNode.itemUnEnableLabel[pos1_k] |= val_k;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (newLabelNode.remainingCapacity >= 0) {
                            computeLocalBound(a + 1, newLabelNode);
                            // addNode
                            if (newLabelNode.minReducedCost < minReducedCost) {
                                newLabelNode.packedItemList = newPackedItemList;
                                nextLabelNodeList.add(newLabelNode);
                                if (newLabelNode.reducedCost < minReducedCost) {
                                    BitSet bitSet = new BitSet(n);
                                    for (Item item : newPackedItemList) {
                                        bitSet.set(item.id);
                                    }
                                    StringBuilder key = new StringBuilder();
                                    key.append(binType.id + "-");
                                    for (int i = 0; i < n; i++) {
                                        if (bitSet.get(i)) key.append(i).append("@");
                                    }
                                    // 不重复pattern
                                    if (!allPatternKeys.contains(key.toString())) {
                                        allPatternKeys.add(key.toString());
                                        Pattern pattern = new Pattern();
                                        pattern.reducedCost = newLabelNode.reducedCost;
                                        pattern.placeItems = Item.copy(newLabelNode.packedItemList);
                                        pattern.binType = binType;
                                        pattern.bitSet = (BitSet) bitSet.clone();
                                        pattern.key = key.toString();
                                        patterns.add(pattern);
                                        minReducedCost = newLabelNode.reducedCost;
                                    }
                                }
                            }
                        }
                    }

                    // 将该物品不打包后与 idx 绑定的物品 i 全部不能打包
                    if ((labelNode.itemUnEnableLabel[pos1_a] & val_a) == 0) {
                        for (int i : eachItemSameIndexList[a]) {
                            int pos1_i = i / LONG_SIZE;
                            int pos2_i = i % LONG_SIZE;
                            long val_i = 1L << pos2_i;
                            labelNode.itemUnEnableLabel[pos1_i] |= val_i;
                        }
                    }
                    computeLocalBound(a + 1, labelNode);
                    // addNode
                    if (labelNode.minReducedCost < minReducedCost) nextLabelNodeList.add(labelNode);
                }
                currentLabelNodeList = nextLabelNodeList;
                nextLabelNodeList = new ArrayList<>();

                if (currentLabelNodeList.size() > maxLabelCnt) break;
            }
            a++;
        }
    }

    public List<Pattern> solve() {
        init();
        labelSetting();
        return patterns;
    }
}
