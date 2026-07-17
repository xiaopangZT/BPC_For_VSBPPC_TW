package com.xiaopangxianren.solver;

import com.xiaopangxianren.classes.*;
import com.xiaopangxianren.utils.CommonUtil;
import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.util.*;

public class RmpSolver {
    public IloCplex cplex;
    public IloRange[] constrains;
    public List<IloRange> srRanges;
    public List<IloNumVar> vars;
    public Node node;
    public List<Pattern> patterns;
    public double objValue;
    public double[] x;
    private List<Pattern> initPatterns;
    public IloObjective objective;
    private List<Item> items;
    private List<BinType> binTypes;

    public Set<String> allPatternsKeySet;

    public List<SR_Cut> srCutList;
    public Set<String> srCutKeySet;

    public int n;
    private int curN;
    private List<List<Item>> compatibilitySet;
    private double[] itemsConstraintDuals;

    public double[] lowerBoundReducedCostDim;

    private IloRange[] binTypeLbConstrains;
    private IloRange[] binTypeUbConstrains;
    private double[] binTypeBoundDualVal; // 各类箱子上下界的对偶值
    private BitSet[] conflictMatrix;

    public RmpSolver(Node node, List<Pattern> initPatterns, List<Item> items, List<BinType> binTypes,
                     List<List<Item>> compatibilitySet, int[] Ls, int[] Us, BitSet[] conflictMatrix) throws IloException {
        this.node = node;
        this.initPatterns = new ArrayList<>(initPatterns);
        this.items = items;
        this.binTypes = new ArrayList<>(binTypes);
        this.compatibilitySet = compatibilitySet;
        this.lowerBoundReducedCostDim = new double[binTypes.size()];
        this.binTypeBoundDualVal = new double[binTypes.size()];
        this.conflictMatrix = conflictMatrix;

        allPatternsKeySet = new HashSet<>();
        n = items.size();

        srRanges = new ArrayList<>();
        srCutList = new ArrayList<>();
        srCutKeySet = new HashSet<>();

        init(Ls, Us);
    }

    private void init(int[] Ls, int[] Us) throws IloException {
        cplex = new IloCplex();
        cplex.setOut(null);
        cplex.setWarning(null);
        // 设置cplex为单线程运行
        cplex.setParam(IloCplex.Param.Threads, 1);
        constrains = new IloRange[n];

        binTypeLbConstrains = new IloRange[binTypes.size()];
        binTypeUbConstrains = new IloRange[binTypes.size()];

        objective = cplex.addMinimize();
        vars = new ArrayList<>();
        patterns = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            constrains[i] = cplex.addGe(cplex.linearNumExpr(), 1);
        }

        for (int i = 0; i < binTypes.size(); i++) {
            int index = binTypes.get(i).id;
            binTypeLbConstrains[index] = cplex.addGe(cplex.linearNumExpr(), Ls[index]);
            binTypeUbConstrains[index] = cplex.addLe(cplex.linearNumExpr(), Us[index]);
        }

        initPatterns.sort((o1, o2) -> Integer.compare(o1.binType.cost, o2.binType.cost));
        for (Pattern pattern : initPatterns) {
            if (allPatternsKeySet.add(pattern.key)) {
                addPatternToModel(pattern, true);
            }
        }

    }

    public void addPatternToModel(Pattern pattern, boolean isAddToMOdel) throws IloException {
        if (isAddToMOdel) {
            IloColumn column = cplex.column(objective, pattern.binType.cost);
            for (Item item : pattern.placeItems) {
                column = column.and(cplex.column(constrains[item.id], 1));
            }

            // binType bound
            int binTypeIndex = pattern.binType.id;
            column = column.and(cplex.column(binTypeLbConstrains[binTypeIndex], 1));
            column = column.and(cplex.column(binTypeUbConstrains[binTypeIndex], 1));

            // src
            for (int i = 0; i < srCutList.size(); i++) {
                int cnt = 0;
                int[] indexs = srCutList.get(i).indexs;
                for (int index : indexs) {
                    if (pattern.bitSet.get(index)) cnt++;
                }
                if (cnt >= 2) {
                    column = column.and(cplex.column(srRanges.get(i), 1));
                } else {
                    column = column.and(cplex.column(srRanges.get(i), 0));
                }
            }

            vars.add(cplex.numVar(column, 0, Double.MAX_VALUE));
        }
        patterns.add(pattern);
    }

    public void addSRCut(SR_Cut srCut) {
        try {
            int addExp = 0;
            double sum = 0d;
            IloLinearNumExpr expr = cplex.linearNumExpr();
            int[] indexs = srCut.indexs;
            for (int i = 0; i < patterns.size(); i++) {
                Pattern pattern = patterns.get(i);
                int cnt = 0;
                for (int index : indexs) if (pattern.bitSet.get(index)) cnt++;
                if (cnt >= 2) {
                    expr.addTerm(1, vars.get(i));
                    sum += x[i];
                    addExp++;
                }
            }
            if (addExp <= 1) {
                System.err.println("Added cut has no effect on the model: " + srCut + " , addExp: " + addExp);
            }
            if (CommonUtil.compareDouble(sum, 1d) <= 0) {
                System.err.println("The sum reducedCostLong of added cut is less than or equal to 1 : " + sum + " , " + srCut);
            }
            srRanges.add(cplex.addLe(expr, 1));
            srCutList.add(srCut);
            srCutKeySet.add(srCut.key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean generateNewPatternsWithBound(double UB, double LB) throws IloException {
        // 是否没有找全所有列
        boolean ret = false;
        int currentPatternNum = 0;
        for (BinType binType : binTypes) {
            for (List<Item> itemList : compatibilitySet) {
                if (itemList.isEmpty()) continue;
                Item[] copyItems = Item.copy(itemList).toArray(new Item[0]);
                // 获取物品的对偶值
                for (Item item : copyItems) {
                    item.dualVal = itemsConstraintDuals[item.id];
                    item.unitVal = item.dualVal / item.volume;
                }
                Arrays.sort(copyItems, (o1, o2) -> {
                    int c = Double.compare(o2.unitVal, o1.unitVal);
                    return c == 0 ? Double.compare(o2.dualVal, o1.dualVal) : c;
                });
                int curN = copyItems.length;
                for (int i = 0; i < curN; i++) {
                    copyItems[i].index = i;
                }

                boolean[] have = new boolean[n];
                // 记录原物品排序后的位置
                Integer[] idIndexMap = new Integer[n];
                for (Item item : copyItems) {
                    idIndexMap[item.id] = item.index;
                    have[item.id] = true;
                }

                Label_Setting_Pricing_For_Enu lsp = new Label_Setting_Pricing_For_Enu(n, curN, currentPatternNum, UB, LB,
                        binType, copyItems, allPatternsKeySet, binTypeBoundDualVal[binType.id], conflictMatrix, idIndexMap, have);
                patterns.addAll(lsp.solve());
                if (!ret) ret = lsp.isOver;
                if (ret) return ret;

                currentPatternNum = patterns.size();

                if (currentPatternNum >= Parameter.MAX_PATTERN_NUM) {
                    System.out.println("Current pattern num reaches the max limit: " + Parameter.MAX_PATTERN_NUM);
                    ret = true;
                    return ret;
                }
            }
        }
        // 缩减列枚举的列
        System.out.println("Total patterns generated before deduplication: " + patterns.size());
        patterns = CommonUtil.Deduplication(patterns);
        System.out.println("Total patterns generated after deduplication: " + patterns.size());

        return ret;
    }

    public DoubleValue_SR_Cut[] getReducedActivateSrCuts(List<DoubleValue_SR_Cut> initSRCutList, Item[] doubleValue_Items) {
        List<DoubleValue_SR_Cut> reducedActivateSrCutList = new ArrayList<>(initSRCutList.size());
        boolean[] used = new boolean[n];
        for (Item item : doubleValue_Items) used[item.id] = true;
        for (DoubleValue_SR_Cut sr : initSRCutList) {
            int c = 0;
            for (int index : sr.indexs) {
                if (index == -1) break;
                if (used[index]) c++;
            }
            if (c >= 2) reducedActivateSrCutList.add(sr);
        }
        int size = reducedActivateSrCutList.size();
        DoubleValue_SR_Cut[] reducedActivateSrCuts = new DoubleValue_SR_Cut[size];
        for (int i = 0; i < size; i++) {
            reducedActivateSrCuts[i] = reducedActivateSrCutList.get(i);
        }
        return reducedActivateSrCuts;
    }

    private Item[] removeZeroDualValItems(Item[] items, int[] father, List<DoubleValue_SR_Cut> initSRCutList) {
        List<Item> newItems = new ArrayList<>();
        int originalN = items.length;
        for (int i = 0; i < originalN; i++) {
            boolean canBeRemoved = true;
            Item itemI = items[i];
            if (itemI.dualVal > 0) {
                canBeRemoved = false;
            } else {
                // 如果与物品i绑定的物品中有对偶值大于0的物品，则不能删除物品i
                int itemI_id = itemI.id;
                for (int j = 0; j < originalN; j++) {
                    Item itemJ = items[j];
                    if (father[itemI_id] == father[itemJ.id] && itemJ.dualVal > 0) {
                        canBeRemoved = false;
                        break;
                    }
                }
                // 如果物品i出现在src中，则不能删除物品i
                if (canBeRemoved) {
                    for (DoubleValue_SR_Cut sr : initSRCutList) {
                        if (sr.containsIndex(i)) {
                            canBeRemoved = false;
                            break;
                        }
                    }
                }
            }
            if (!canBeRemoved) newItems.add(itemI.copy());
        }
        Item[] ret = new Item[newItems.size()];
        for (int i = 0; i < newItems.size(); i++) {
            ret[i] = newItems.get(i);
        }
        return ret;
    }

    public List<Pattern> generateNewPatterns(boolean isRoot) throws IloException {
        Arrays.fill(lowerBoundReducedCostDim, 0d);
        // Clone branch pairs because their indices are remapped below.
        List<int[]> bindList = new ArrayList<>(node.bindList.size());
        for (int[] bind : node.bindList) bindList.add(bind.clone());
        curN = n;

        List<Pattern> newPatterns = new ArrayList<>();

        // 构建相互绑定物品的并查集
        UnionFind originalItemsUf = new UnionFind(curN);
        for (int[] bind : bindList) {
            originalItemsUf.join(bind[0], bind[1]);
        }
        int[] originalItemsFat = new int[curN];
        for (int i = 0; i < curN; i++) {
            originalItemsFat[i] = originalItemsUf.find(i);
        }

        for (List<Item> copyItemList : compatibilitySet) {
            if (copyItemList.isEmpty()) continue;

            // SR cut
            List<DoubleValue_SR_Cut> activatedSrCutList = new ArrayList<>();
            for (int i = 0; i < srCutList.size(); i++) {
                double doubleValue = cplex.getDual(srRanges.get(i));
                if (doubleValue != 0) {
                    SR_Cut sr = srCutList.get(i);
                    activatedSrCutList.add(new DoubleValue_SR_Cut(sr.indexs.clone(), doubleValue));
                }
            }

            Item[] copyItems = Item.copy(copyItemList).toArray(new Item[0]);
            for (Item item : copyItems) {
                item.dualVal = itemsConstraintDuals[item.id];
                item.unitVal = item.dualVal / item.volume;
            }

            Item[] notRemovedItems = removeZeroDualValItems(copyItems, originalItemsFat, activatedSrCutList);
            DoubleValue_SR_Cut[] reducedActivateSrCuts = getReducedActivateSrCuts(activatedSrCutList, notRemovedItems);

            Arrays.sort(notRemovedItems, (o1, o2) -> {
                int c = Double.compare(o2.unitVal, o1.unitVal);
                return c == 0 ? Double.compare(o2.dualVal, o1.dualVal) : c;
            });
            int curN = notRemovedItems.length;
            // 重新设置索引
            for (int i = 0; i < curN; i++) {
                notRemovedItems[i].index = i;
            }
            boolean[] have = new boolean[n];
            // 记录原物品排序后的位置
            Integer[] idIndexMap = new Integer[n];
            for (Item item : notRemovedItems) {
                idIndexMap[item.id] = item.index;
                have[item.id] = true;
            }

            // 修改绑定约束
            List<int[]> newBindList = new ArrayList<>();
            for (int[] bind : bindList) {
                if (have[bind[0]] && have[bind[1]]) {
                    bind[0] = idIndexMap[bind[0]];
                    bind[1] = idIndexMap[bind[1]];
                    newBindList.add(bind);
                }
            }
            bindList = newBindList;

            // 将绑定物品添加到并查集中
            UnionFind unionFind = new UnionFind(curN);
            for (int[] bind : bindList)
                unionFind.join(bind[0], bind[1]);
            int[] fat = new int[curN];
            for (int i = 0; i < curN; i++) fat[i] = unionFind.find(i);

            // 修改SR约束
            for (DoubleValue_SR_Cut srCut : reducedActivateSrCuts) {
                int[] indexs = srCut.indexs;
                for (int i = 0; i < 3; i++) {
                    Integer j = idIndexMap[indexs[i]];
                    if (j == null) {
                        indexs[i] = -1;
                    } else {
                        indexs[i] = j;
                    }
                }
            }

            int index = 0;
            for (BinType binType : binTypes) {
                double[] dp = new double[binType.capacity + 1];
                for (Item copyItem : notRemovedItems) {
                    int w = copyItem.volume;
                    double v = copyItem.dualVal;
                    for (int j = binType.capacity; j >= w; j--) {
                        dp[j] = Math.max(dp[j], dp[j - w] + v);
                    }
                }
                Label_Setting_Pricing lsp = new Label_Setting_Pricing(n, curN, binType, notRemovedItems, fat,
                        reducedActivateSrCuts, allPatternsKeySet, dp, binTypeBoundDualVal[binType.id], node.branchConstraintMatrix,
                        idIndexMap, have);

                newPatterns.addAll(lsp.solve());
                lowerBoundReducedCostDim[index] = Math.min(lowerBoundReducedCostDim[index], lsp.minReducedCost);
                index++;
            }
        }

        newPatterns = CommonUtil.Deduplication(newPatterns);
        return newPatterns;
    }

    public boolean solveRmp() throws IloException {
        if (!cplex.solve()) {
            return false;
        }
        objValue = cplex.getObjValue();
        // 得到每个物品的对偶值
        itemsConstraintDuals = cplex.getDuals(constrains);
        for (int i = 0; i < n; i++) {
            if (Math.abs(itemsConstraintDuals[i]) < 1e-9) {
                itemsConstraintDuals[i] = 0.0;
            }
            items.get(i).dualVal = itemsConstraintDuals[i];
            items.get(i).unitVal = itemsConstraintDuals[i] / items.get(i).volume;
        }
        // 得到x值
        x = new double[vars.size()];
        for (int i = 0; i < vars.size(); i++) {
            x[i] = cplex.getValue(vars.get(i));
            // 设置pattern的x值
            patterns.get(i).xVal = x[i];
        }

        // 得到binType bound的对偶值
        Arrays.fill(binTypeBoundDualVal, 0);
        for (int i = 0; i < binTypes.size(); i++) {
            double dualValLb = cplex.getDual(binTypeLbConstrains[i]);
            double dualValUb = cplex.getDual(binTypeUbConstrains[i]);
            if (Math.abs(dualValLb) < 1e-9) {
                dualValLb = 0.0;
            }
            if (Math.abs(dualValUb) < 1e-9) {
                dualValUb = 0.0;
            }
            binTypeBoundDualVal[i] = (dualValLb + dualValUb);
        }
        return true;
    }

    public void end() throws IloException {
        cplex.end();
    }
}
