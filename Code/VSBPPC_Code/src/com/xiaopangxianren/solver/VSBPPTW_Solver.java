package com.xiaopangxianren.solver;

import com.xiaopangxianren.classes.*;
import com.xiaopangxianren.utils.CommonUtil;
import com.xiaopangxianren.utils.TimeUtil;
import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.util.*;

import static com.xiaopangxianren.utils.CommonUtil.EPS;

public class VSBPPTW_Solver {
    public Solution bestSolution;
    private List<BinType> binTypes;
    private List<Item> items;
    private int n, S;
    private Node rootNode;
    private static final int MaxSrCutNum = 100;
    private boolean diving;
    // 箱子类型可能的组合值
    private boolean[] possibleCombination;
    private Instance instance;
    private Random random;
    private int[] Ls, Us;
    // 所有的最大兼容性集合
    private List<List<Item>> compatibilitySet = new ArrayList<>();
    private BitSet[] conflictMatrix;

    public VSBPPTW_Solver(List<BinType> binTypes, List<Item> items, Instance instance) {
        this.binTypes = binTypes;
        this.items = items;
        this.instance = instance;
        bestSolution = new Solution();
        n = items.size();
        // 获取最大箱子容量
        this.S = binTypes.stream().mapToInt(binType -> binType.capacity).max().orElse(0);
        int maxCost = binTypes.stream().mapToInt(binType -> binType.cost).max().orElse(0);
        possibleCombination = new boolean[maxCost * n + 1];
        random = new Random(929L * n * binTypes.size());

        // 箱子类型按容量升序
        binTypes.sort(Comparator.comparingInt(binType -> binType.capacity));
        for (int i = 0; i < binTypes.size(); i++) {
            binTypes.get(i).id = i;
        }
    }

    private boolean roundByIntRmpSolution(RmpSolver rmpSolver) throws IloException {
        boolean isInt = true;
        int len = rmpSolver.x.length;
        for (int i = 0; i < len; i++) {
            double x = rmpSolver.x[i];
            if ((x > 0.001 && x < 0.999) || !(Math.abs(x - Math.round(x)) < Parameter.EPS)) {
                isInt = false;
                break;
            }
        }
        // 更新上界
        if (isInt && Math.ceil(rmpSolver.objValue) < bestSolution.UB) {
            int cost = 0;
            bestSolution.bins = new ArrayList<>();
            for (int i = 0; i < len; i++) {
                if (rmpSolver.x[i] > 0.5) {
                    Bin bin = CommonUtil.createBinFromPattern(rmpSolver.patterns.get(i), conflictMatrix);
                    bestSolution.bins.add(bin);
                    cost += bin.binType.cost;
                }
            }
            if (cost < bestSolution.UB) {
                bestSolution.UB = cost;
                if (bestSolution.UB == bestSolution.LB)
                    bestSolution.isOpt = true;
                System.out.println("\t Find better integer solution by rmp solution," + bestSolution.UB);
                return true;
            }
        }
        return false;
    }

    private int roundingByIp(List<Pattern> patternList, double solveTime_s, boolean isEnu) throws IloException {
        IloCplex cplex = new IloCplex();
        cplex.setOut(null);
        cplex.setWarning(null);
        // 设置求解器只使用单线程
        cplex.setParam(IloCplex.IntParam.Threads, 1);
        // 设置模型求解时间
        cplex.setParam(IloCplex.DoubleParam.TiLim, solveTime_s);
        if (isEnu) {
            cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, Parameter.EPS);
            cplex.setParam(IloCplex.Param.MIP.Tolerances.AbsMIPGap, Parameter.EPS);
        }
        double startTime = cplex.getCplexTime();

        IloIntVar[] x = cplex.boolVarArray(patternList.size());
        IloLinearNumExpr objective = cplex.linearNumExpr();
        for (int i = 0; i < patternList.size(); i++) {
            objective.addTerm(patternList.get(i).binType.cost, x[i]);
        }

        cplex.addMinimize(objective);
        cplex.addLe(objective, bestSolution.UB - 1);
        for (int i = 0; i < n; i++) {
            IloLinearNumExpr expr = cplex.linearNumExpr();
            for (int j = 0; j < patternList.size(); j++) {
                if (patternList.get(j).bitSet.get(i))
                    expr.addTerm(1, x[j]);
            }
            cplex.addGe(expr, 1);
        }

        // 求解模型
        if (cplex.solve()) {
            int objVal = (int) Math.round(cplex.getObjValue());
            System.out.println("IP solve status: " + cplex.getStatus());
            System.out.println("IP rounding objVal = " + objVal);
            //  更新上界
            if (objVal < bestSolution.UB) {
                bestSolution.UB = objVal;
                bestSolution.bins.clear();
                for (int i = 0; i < patternList.size(); i++) {
                    if (cplex.getValue(x[i]) > 0.5) {
                        Bin bin = CommonUtil.createBinFromPattern(patternList.get(i), conflictMatrix);
                        bestSolution.bins.add(bin);
                    }
                }
                if (bestSolution.UB == bestSolution.LB) bestSolution.isOpt = true;
                System.out.println("\t Find better solution by IPRounding " + bestSolution.UB);
            }
            IloCplex.Status status = cplex.getStatus();
            if (status != IloCplex.Status.Optimal) {
                // 不是最优解，返回-1
                System.out.println("IP rounding not optimal solution");
                cplex.end();
                return -1;
            }
            cplex.end();
            // 求解成功，返回0
            return 0;
        } else {
            System.out.println("IP rounding no feasible solution");
        }
        double soloveTime = cplex.getCplexTime();
        cplex.end();
        if ((soloveTime - startTime) >= solveTime_s) {
            // 超时，返回-1
            System.out.println("solve IP rounding model time limit");
            return -1;
        } else {
            // 无可行解，返回-2
            return -2;
        }
    }

    private void roundingByHeuristic(RmpSolver rmpSolver) {
        int halfUB = (bestSolution.UB) / 2;
        // 按照lp值降序排列
        List<Pattern> copyPatternList = new ArrayList<>(rmpSolver.patterns);
        copyPatternList.sort((o1, o2) -> Double.compare(o2.xVal, o1.xVal));
        int cost = 0;
        boolean[] used = new boolean[n];
        List<Pattern> candidatePatternList = new ArrayList<>();
        for (Pattern pattern : copyPatternList) {
            boolean canAdd = true;
            for (Item item : pattern.placeItems) {
                if (used[item.id]) {
                    canAdd = false;
                    break;
                }
            }
            if (canAdd) {
                candidatePatternList.add(pattern);
                cost += pattern.binType.cost;
                for (Item item : pattern.placeItems) {
                    used[item.id] = true;
                }
                if (cost >= halfUB) break;
            }
        }
        // 得到未装箱的物品
        List<Item> unpackedItemList = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!used[i]) {
                unpackedItemList.add(items.get(i));
            }
        }

        if (!unpackedItemList.isEmpty()) {
            HeuristicAlgorithm heuristicAlgorithm = new HeuristicAlgorithm(conflictMatrix);
            Sol sol = new Sol(unpackedItemList.size());
            heuristicAlgorithm.leastCompFirstHeur(new Instance(binTypes, unpackedItemList), sol, false, null);
            for (Bin bin : sol.bins) {
                Pattern pattern = CommonUtil.generatePatternByPlaceItemList(n, bin.binType, bin.placedItemList);
                candidatePatternList.add(pattern);
                cost += pattern.binType.cost;
            }
        }

        if (cost < bestSolution.UB) {
            bestSolution.UB = cost;
            bestSolution.bins = new ArrayList<>();
            for (Pattern pattern : candidatePatternList) {
                Bin bin = CommonUtil.createBinFromPattern(pattern, conflictMatrix);
                bestSolution.bins.add(bin);
            }
            if (bestSolution.UB == bestSolution.LB)
                bestSolution.isOpt = true;
            System.out.println("\t Find better solution by heuristic rounding," + bestSolution.UB);
        }
    }

    private void rounding(RmpSolver solver, Node node) throws IloException {
        // 判断当前解是否为整数解
        boolean isInt = roundByIntRmpSolution(solver);
        if (!isInt && !bestSolution.isOpt && bestSolution.LB < bestSolution.UB)
            roundingByHeuristic(solver);
    }

    private int getEffectiveLowerBound(int lb) {
        int effectiveLB = lb;
        while (!possibleCombination[effectiveLB]) {
            effectiveLB++;
        }
        return effectiveLB;
    }

    private List<SR_Cut> findSRCs_3(List<SR_Cut> newSRCutList, int[] fat, RmpSolver rmpSolver) {
        // 如果已有SRC数量达到上限，直接返回空列表
        if (rmpSolver.srCutList.size() >= MaxSrCutNum) return new ArrayList<>();
        long s = System.currentTimeMillis();
        // 记录每种item存在于哪些系数大于0的列中
        // 建立每个物品出现在哪些有效列中的索引。
        ArrayList<ArrayList<Integer>> columnIdx = new ArrayList<>();
        for (int i = 0; i < n; i++) columnIdx.add(new ArrayList<>());
        for (int i = 0; i < rmpSolver.patterns.size(); i++) {
            Pattern pattern = rmpSolver.patterns.get(i);
            if (rmpSolver.x[i] > 0) {
                for (Item item : pattern.placeItems) columnIdx.get(item.id).add(i);
            }
        }
        // 避免为绑定物品重复生成相同的SRC，只考虑独立物品
        ArrayList<HashSet<Integer>> columnSet = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            HashSet<Integer> columnSetI = new HashSet<>();
            // 将物品i出现的列加入columnSetI
            if (fat[i] == i) columnSetI.addAll(columnIdx.get(i));
            columnSet.add(columnSetI);
        }
        // 开始寻找Cut（只针对独立物品）
        for (int i = 0; i < n; i++) {
            if (fat[i] == i) {
                Item itemI = items.get(i);
                HashSet<Integer> columnSetI = columnSet.get(i);
                for (int j = i + 1; j < n; j++) {
                    Item itemJ = items.get(j);
                    if (fat[j] == j && itemI.volume + itemJ.volume <= S) {
                        // 计算同时包含i和j的列的总系数
                        HashSet<Integer> columnSetJ = columnSet.get(j);
                        double sumOfValueOfIJ = 0d;
                        for (int p : columnSetI) {
                            if (columnSetJ.contains(p)) sumOfValueOfIJ += rmpSolver.x[p];
                        }
                        // 只有当ij两个人的和大于0时，寻找第三个item才是有希望的
                        if (sumOfValueOfIJ > 0) {
                            for (int k = j + 1; k < n; k++) {
                                Item itemK = items.get(k);
                                if (fat[k] == k && itemI.volume + itemK.volume <= S && itemJ.volume + itemK.volume <= S) {
                                    HashSet<Integer> columnSetK = columnSet.get(k);
                                    double sumValue = sumOfValueOfIJ;
                                    // 遍历包含物品k的列
                                    for (int p : columnSetK) {
                                        int cnt = 0;
                                        if (columnSetI.contains(p)) cnt++; // 列p是否包含i
                                        if (columnSetJ.contains(p)) cnt++; // 列p是否包含j
                                        // 只包含i或j中的一个
                                        if (cnt == 1) sumValue += rmpSolver.x[p];
                                    }
                                    if (sumValue > 1 + EPS) {
                                        SR_Cut sr = new SR_Cut(new int[]{i, j, k}, sumValue);
                                        // 不添加重复的约束
                                        if (!rmpSolver.srCutKeySet.contains(sr.key)) newSRCutList.add(sr);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        try {
            // 按违反程度降序排序
            newSRCutList.sort((o1, o2) -> -CommonUtil.compareDouble(o1.coefficient, o2.coefficient));
        } catch (Exception e) {
            newSRCutList.sort((o1, o2) -> -Double.compare(o1.coefficient, o2.coefficient));
        }
        List<SR_Cut> newSRs = new ArrayList<>();
        for (SR_Cut sr : newSRCutList) {
            newSRs.add(sr);
            if (newSRs.size() + rmpSolver.srCutList.size() >= MaxSrCutNum) break;
        }
        System.out.println("\t Total Added 3-SRCs: " + newSRs.size());
        System.out.println("\t findSRCs_3 time = " + (System.currentTimeMillis() - s) + "ms");
        return newSRs;
    }

    private boolean solveNodeByCG(Node node) throws IloException {
        System.out.println("----------------column generate start " + node.key + "----------------");
        // 处理未被使用的item
        boolean[] used = new boolean[n];
        for (Pattern pattern : node.patternList) {
            for (Item item : pattern.placeItems) used[item.id] = true;
        }
        for (int i = 0; i < n; i++) {
            if (!used[i]) {
                System.out.println("item" + i + "，未被使用");
                List<Item> unusedItemList = new ArrayList<>();
                for (int j = 0; j < n; j++) {
                    // 将和i绑定的物品全加入到未使用的物品列表中
                    if (node.father[i] == node.father[j]) {
                        unusedItemList.add(items.get(j));
                    }
                }
                int usedVolume = unusedItemList.stream().mapToInt(Item::getVolume).sum();
                // 将未使用的物品生成新模式
                BinType selectedBinType = CommonUtil.findBestFitBinType(usedVolume, binTypes);
                if (selectedBinType != null) {
                    BitSet bitSet = new BitSet();
                    for (Item item : unusedItemList) {
                        bitSet.set(item.id);
                    }
                    Pattern pattern = new Pattern(selectedBinType, n, unusedItemList);
                    pattern.bitSet = bitSet;
                    pattern.genKey();
                    node.patternList.add(pattern);
                }
            }
        }

        RmpSolver rmpSolver = new RmpSolver(node, node.patternList, items, binTypes, compatibilitySet, Ls, Us, conflictMatrix);
        int iter = 0;
        int oldColCnt = rmpSolver.patterns.size();
        long startTime = System.currentTimeMillis();
        int localIntLowerBound = node.lb;
        double localFraLowerBound = localIntLowerBound;

        while (true) {
            /* **************************列生成开始****************************** */
            while (!bestSolution.isOpt && !TimeUtil.isTimeLimit()) {
                long innerStartTime = System.currentTimeMillis();
                iter++;
                bestSolution.rmpCnt++;
                if (!rmpSolver.solveRmp()) {
                    rmpSolver.end();
                    System.err.println("RMP solve failed.");
                    return false;
                }
                long rmpTime = System.currentTimeMillis() - innerStartTime;
                bestSolution.rmpTime += rmpTime;

                System.out.println("objVal = " + rmpSolver.objValue);
                System.out.println("rmpSolver.solveRmp() time = " + rmpTime + "ms");
                // 尝试更新上界
                rounding(rmpSolver, node);
                if (bestSolution.isOpt)
                    break;
                List<Pattern> newPattern = null;
                newPattern = rmpSolver.generateNewPatterns(node.isRoot);
                bestSolution.pricingCnt++;
                long pricingTime = System.currentTimeMillis() - innerStartTime - rmpTime;
                bestSolution.pricingTime += pricingTime;
                System.out.println("newPattern.size() = " + newPattern.size() + ", pricing time = " + pricingTime + "ms");
                if (newPattern.isEmpty()) {
                    if (!TimeUtil.isTimeLimit()) {
                        localFraLowerBound = Math.max(localFraLowerBound, rmpSolver.objValue);
                        localIntLowerBound = Math.max(localIntLowerBound, CommonUtil.ceilToInt(localFraLowerBound));
                    }
                    break;
                }

                double temp_value1 = rmpSolver.objValue;
                for (int i = 0; i < rmpSolver.lowerBoundReducedCostDim.length; i++) {
                    if (rmpSolver.lowerBoundReducedCostDim[i] < 0)
                        temp_value1 += Us[i] * rmpSolver.lowerBoundReducedCostDim[i];
                }
                double minCost = binTypes.stream().min(Comparator.comparingInt(binType -> binType.cost)).orElseThrow().cost;
                double minReducedCost = rmpSolver.lowerBoundReducedCostDim[0];
                for (int i = 1; i < rmpSolver.lowerBoundReducedCostDim.length; i++) {
                    if (rmpSolver.lowerBoundReducedCostDim[i] < minReducedCost) {
                        minReducedCost = rmpSolver.lowerBoundReducedCostDim[i];
                    }
                }
                double temp_value2 = rmpSolver.objValue + (bestSolution.UB / minCost * minReducedCost);
                localFraLowerBound = Math.max(localFraLowerBound, temp_value1);
                localFraLowerBound = Math.max(localFraLowerBound, temp_value2);
                localIntLowerBound = Math.max(localIntLowerBound, CommonUtil.ceilToInt(localFraLowerBound));

                for (Pattern pattern : newPattern)
                    rmpSolver.addPatternToModel(pattern, true);
                System.out.println("CG one iter time = " + (System.currentTimeMillis() - innerStartTime) + "ms");
                System.out.println();

                // 是否提前跳出
                if (Parameter.EarlyTerminationEnable) {
                    if (localIntLowerBound >= bestSolution.UB) {
                        System.out.println("Node can be pruned: ceil(" + localFraLowerBound + ") >= " + localIntLowerBound);
                        if (node.isRoot) {
                            bestSolution.ET_Root = true;
                        } else {
                            bestSolution.ET_Enum++;
                        }
                        break;
                    }
                    if (localIntLowerBound >= CommonUtil.ceilToInt(rmpSolver.objValue)) {
                        System.out.println("Early break by the valid lower bound: " + rmpSolver.objValue + " , " + localFraLowerBound);
                        rmpSolver.solveRmp();
                        if (node.isRoot) {
                            bestSolution.ET_Root = true;
                        } else {
                            bestSolution.ET_Enum++;
                        }
                        break;
                    }
                }
            }
            // 如果列生成失败，直接返回false
            if (rmpSolver.x == null || TimeUtil.isTimeLimit()) {
                rmpSolver.end();
                return false;
            }

            node.lb = Math.max(CommonUtil.ceilToInt(rmpSolver.objValue), node.lb);
            node.lb = getEffectiveLowerBound(node.lb);
            if (node.isRoot) {
                bestSolution.rootColCnt = rmpSolver.patterns.size();
                bestSolution.LB = Math.max(bestSolution.LB, node.lb);
                bestSolution.LB = getEffectiveLowerBound(bestSolution.LB);
                if (bestSolution.LB == bestSolution.UB) {
                    bestSolution.isOpt = true;
                    rmpSolver.end();
                    return false;
                }
            }

            if (bestSolution.isOpt) {
                bestSolution.exploredNodeCnt++;
                rmpSolver.end();
                return false;
            }

            if (node.lb >= bestSolution.UB) {
                bestSolution.exploredNodeCnt++;
                rmpSolver.end();
                System.out.println("this node can be fathomed by LB >= UB");
                return false;
            }

            /* **************************列生成结束*******************************/
            if (!Parameter.isUseSR_Cut) break;
            List<SR_Cut> newSRCutList = new ArrayList<>();
            double UB_LB_Gap = (double) (bestSolution.UB - node.lb) / bestSolution.UB;
            if (UB_LB_Gap >= 0 && UB_LB_Gap <= 1) {
                newSRCutList = findSRCs_3(newSRCutList, node.father, rmpSolver);
            }
            if (newSRCutList.isEmpty()) {
                break;
            } else {
                if (node.isRoot) bestSolution.rootSrCnt += newSRCutList.size();
                bestSolution.totalSrCnt += newSRCutList.size();
                for (SR_Cut sr : newSRCutList) rmpSolver.addSRCut(sr);
            }
        }
        node.x = rmpSolver.x.clone();
        node.patternList = new ArrayList<>(rmpSolver.patterns);
        double dualGap = (bestSolution.UB - rmpSolver.objValue) / bestSolution.UB;

        if (!bestSolution.isOpt && bestSolution.LB < bestSolution.UB) {
            // Root node 更积极，非 root 保守
            boolean doHeuristicIP = (node.isRoot && dualGap < 0.05) || (!node.isRoot && dualGap < 0.01 && node.deep % 5 == 0);

            if (doHeuristicIP) {
                double timeLimit = node.isRoot ? 5.0 : 0.5;
                long ipStartTime = System.currentTimeMillis();
                roundingByIp(rmpSolver.patterns, timeLimit, false);
                System.out.println("small IP time = "
                        + (System.currentTimeMillis() - ipStartTime) + " ms");
            }
        }

        System.out.println("cg time = " + (System.currentTimeMillis() - startTime) + " ms");
        System.out.println("UB = " + bestSolution.UB + ", LB = " + node.lb + ", dualGap = " + dualGap);

        if (node.isRoot && !bestSolution.isOpt && Parameter.isUsePatternEnum) {
            bestSolution.patternEnuCnt++;
            // 列枚举算法开始时间
            long enuStartTime = System.currentTimeMillis();
            System.out.println("current UB = " + bestSolution.UB + ", LB = " + bestSolution.LB);
            System.out.println("before find patterns by enumeration, total patterns size = " + rmpSolver.patterns.size());

            boolean isNoFoundAllPatterns = rmpSolver.generateNewPatternsWithBound(bestSolution.UB, rmpSolver.objValue);
            // 统计枚举时间
            bestSolution.findPatternEnuTime += (System.currentTimeMillis() - enuStartTime);
            System.out.println("isNotFoundAllPatterns = " + isNoFoundAllPatterns);
            System.out.println("Column enumeration time = " + (System.currentTimeMillis() - enuStartTime) + " ms");
            if (!isNoFoundAllPatterns) {
                System.out.println("After column enumeration, total patterns size = " + rmpSolver.patterns.size());

                long finalIpStart = System.currentTimeMillis();
                int ret = roundingByIp(rmpSolver.patterns, TimeUtil.getRemainingTime() / 1000.0, true);

                System.out.println("Final IP time = " + (System.currentTimeMillis() - finalIpStart) + " ms");

                // 如果IP正常结束, 或者无可行解，则证明最优
                if (ret == 0 || ret == -2) {
                    bestSolution.LB = bestSolution.UB;
                    bestSolution.isOpt = true;
                    bestSolution.rootEnu_isOpt = true;
                    System.out.println("Optimal solution proven by column enumeration final IP");
                }
            }
            bestSolution.patternEnuTime += (System.currentTimeMillis() - enuStartTime);
            System.out.println("Total column enumeration time = " + bestSolution.patternEnuTime + " ms");
        }

        rmpSolver.end();
        System.out.println("========break  while=========");
        System.out.println("CG total time = " + (System.currentTimeMillis() - startTime) + "ms");
        System.out.println("iter = " + iter);
        System.out.println("curent node deep = " + node.deep);
        System.out.println("bind num = " + node.bindList.size());
        System.out.println("conflict num = " + node.conflictList.size());
        System.out.println("UB = " + bestSolution.UB);
        System.out.println("node.lb = " + node.lb);
        System.out.println("old column count = " + oldColCnt + ", new column count = " + rmpSolver.patterns.size());
        System.out.println("----------------column generate end----------------");
        System.out.println();

        return true;
    }

    private int[] findBranchItemPair(Node node) {
        int[] branchItemPair = new int[]{-1, -1};
        double bestDiff = 1d;
        int bestSumW = -1;
        double[][] sum = new double[n][n];
        for (int k = 0; k < node.patternList.size(); k++) {
            double v = node.x[k];
            // 找到x值大于0的pattern
            if (v > 0) {
                Pattern pattern = node.patternList.get(k);
                // 遍历模式中放入的物品
                for (int i = 0; i < pattern.placeItems.size(); i++) {
                    int m = pattern.placeItems.get(i).id;
                    for (int j = i + 1; j < pattern.placeItems.size(); j++) {
                        int idj = pattern.placeItems.get(j).id;
                        if (m < idj && node.branchConstraintMatrix[m][idj] == null) {
                            sum[m][idj] += v;
                        } else if (m > idj && node.branchConstraintMatrix[m][idj] == null) {
                            sum[idj][m] += v;
                        } else if (m == idj) {
                            throw new RuntimeException("find item index exit m == n");
                        }
                    }
                }
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (node.branchConstraintMatrix[i][j] == null) {
                    if (!CommonUtil.isInteger(sum[i][j])) {
                        double diff = Math.abs(sum[i][j] - 0.5);
                        // 看谁更接近0.5
                        int c = CommonUtil.compareDouble(diff, bestDiff);
                        if (c < 0) {
                            bestDiff = diff;
                            branchItemPair[0] = i;
                            branchItemPair[1] = j;
                        }
                    }
                }
            }
        }
        if (branchItemPair[0] == -1) {
            return null;
        }
        System.out.println("Find branch item pair: " + Arrays.toString(branchItemPair) + " , " + bestDiff + " , " + bestSumW);
        return branchItemPair;
    }

    // 使用深度优先搜索进行分支定界
    private void branchAndBoundByDfs(Node node) throws IloException {
        if (!bestSolution.isOpt && node.lb < bestSolution.UB && !TimeUtil.isTimeLimit()) {
            int[] branchItemPair = findBranchItemPair(node);
            if (branchItemPair == null) return;
            // 左子节点，绑定物品对
            int[] bind = new int[]{branchItemPair[0], branchItemPair[1]};
            Node leftNode = node.createLeftChild(bind);

            if (diving) {
                bestSolution.exploredDivingNodeCnt++;
            } else {
                bestSolution.exploredNodeCnt++;
                bestSolution.generatedNodeCnt++;
            }
            if (solveNodeByCG(leftNode)) {
                branchAndBoundByDfs(leftNode);
            }
            if (bestSolution.isOpt || TimeUtil.isTimeLimit()) return;
            // 右子节点，冲突物品对
            int[] conflict = branchItemPair.clone();
            Node rightNode = node.createRightChild(conflict);

            if (diving) {
                bestSolution.exploredDivingNodeCnt++;
            } else {
                bestSolution.exploredNodeCnt++;
                bestSolution.generatedNodeCnt++;
            }
            if ((!diving || !node.key.contains(0 + "@")) && solveNodeByCG(rightNode)) {
                branchAndBoundByDfs(rightNode);
            }
        }
    }

    private void addPatternsFromSol(Sol sol, List<Pattern> patterns, Set<String> localKeySet) {
        for (Bin bin : sol.bins) {
            Pattern pattern = CommonUtil.generatePatternByPlaceItemList(n, bin.binType, bin.placedItemList);
            if (localKeySet.add(pattern.key)) {
                patterns.add(pattern);
            }
        }
    }

    private void computeUB() {
        List<Pattern> newPatterns = new ArrayList<>();
        Set<String> localKeySet = new HashSet<>();

        // 启发式搜集列
        HeuristicAlgorithm heuristicAlgorithm = new HeuristicAlgorithm(conflictMatrix);

        Sol sol1 = new Sol(instance.items.size());
        heuristicAlgorithm.leastCompFirstHeur(instance, sol1, false, null);
        addPatternsFromSol(sol1, newPatterns, localKeySet);

        Sol sol2 = new Sol(instance.items.size());
        heuristicAlgorithm.bestFitHeur(instance, sol2, BFType.Min_End_BF1);
        addPatternsFromSol(sol2, newPatterns, localKeySet);

        Sol sol3 = new Sol(instance.items.size());
        heuristicAlgorithm.bestFitHeur(instance, sol3, BFType.Max_Start_BF1rev);
        addPatternsFromSol(sol3, newPatterns, localKeySet);

        int randomCnt = n * 10;
        for (int i = 0; i < randomCnt && newPatterns.size() < 1000; i++) {
            Sol tempSol = new Sol(instance.items.size());
            heuristicAlgorithm.leastCompFirstHeur(instance, tempSol, true, random);
            addPatternsFromSol(tempSol, newPatterns, localKeySet);
        }

        int size = newPatterns.size();
        bestSolution.patterns.addAll(newPatterns);
        System.out.println("Initial patterns size for UB computation: " + size);
        try {
            IloCplex cplex = new IloCplex();
            cplex.setOut(null);
            cplex.setWarning(null);
            cplex.setParam(IloCplex.IntParam.Threads, 1);
            cplex.setParam(IloCplex.DoubleParam.TimeLimit, 2d);

            IloIntVar[] x = cplex.boolVarArray(size);
            IloLinearNumExpr target = cplex.linearNumExpr();
            for (int i = 0; i < size; i++) {
                target.addTerm(x[i], newPatterns.get(i).binType.cost);
            }
            cplex.addMinimize(target);

            for (int i = 0; i < n; i++) {
                IloLinearNumExpr expr = cplex.linearNumExpr();
                for (int j = 0; j < size; j++) {
                    if (newPatterns.get(j).bitSet.get(i)) expr.addTerm(1, x[j]);
                }
                cplex.addGe(expr, 1);
            }

            if (cplex.solve()) {

                for (int i = 0; i < size; i++) {
                    if (cplex.getValue(x[i]) > 0.5) {
                        bestSolution.bins.add(CommonUtil.createBinFromPattern(newPatterns.get(i), conflictMatrix));
                    }
                }
                bestSolution.UB = CommonUtil.ceilToInt(cplex.getObjValue());
            }
            cplex.clearModel();
            cplex.end();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void calculateLbAndUbForBinTypes(int[] Ls, int[] Us, List<BinType> binTypes, List<Item> items, int UB) {
        int binTypeNum = binTypes.size(), n = items.size();
        int[] Cs = new int[binTypeNum];
        double[] halfCapacity = new double[binTypeNum];

        List<Item>[] onlyCanPackedItems = new List[binTypeNum];
        for (int i = 0; i < binTypeNum; i++) {
            BinType binType = binTypes.get(i);
            halfCapacity[i] = binType.capacity / 2.0;
            onlyCanPackedItems[i] = new ArrayList<>();
        }

        for (Item item : items) {
            int c = 0;
            int b = -1;
            for (int i = 0; i < binTypeNum; i++) {
                if (item.volume <= binTypes.get(i).capacity) {
                    c++;
                    b = i;
                }
            }
            if (c == 0) throw new RuntimeException("No bin can pack item " + item.id);
            if (c == 1) {
                onlyCanPackedItems[b].add(item);
                // 如果这个物品的体积大于该箱型的一半，那么至少需要一个箱子来装它
                if (item.volume > halfCapacity[b]) Cs[b]++;
            }
        }

        for (int i = 0; i < binTypeNum; i++) {
            BinType binType = binTypes.get(i);
            List<Item> onlyCanPackedItemList = onlyCanPackedItems[i];
            int s = 0;
            for (Item item : onlyCanPackedItemList) s += item.volume;
            Ls[i] = Math.max(Cs[i], CommonUtil.ceilToInt((double) s / binType.capacity));
        }

        int[] sums = new int[binTypeNum];
        for (int i = 0; i < n; i++) {
            Item item = items.get(i);
            for (int j = 0; j < binTypeNum; j++) {
                if (item.volume <= binTypes.get(j).capacity) {
                    sums[j] += item.volume;
                    break;
                }
            }
        }

        for (int b = binTypeNum - 1; b < binTypeNum; b++) {
            int sum1 = 0;
            int sum2 = 0;
            for (int bPie = b; bPie < binTypeNum; bPie++) {
                sum1 += sums[bPie];
                sum2 += binTypes.get(bPie).capacity;
            }
            int newLb = CommonUtil.ceilToInt((double) sum1 / sum2);
            if (newLb > Ls[b]) Ls[b] = newLb;
        }

        int remainUB = UB;
        for (int b = 0; b < binTypeNum; b++) remainUB -= Ls[b] * binTypes.get(b).cost;
        for (int b = 0; b < binTypeNum; b++) {
            Us[b] = Math.min(Us[b], CommonUtil.ceilToInt((double) remainUB / binTypes.get(b).cost) + Ls[b]);
        }

        for (int i = 0; i < binTypeNum; i++) {
            if (Us[i] < Ls[i]) {
                throw new RuntimeException("Inconsistent LB and UB for bin type " + binTypes.get(i).id);
            }
        }
        System.out.println("Ls = " + Arrays.toString(Ls));
        System.out.println("Us = " + Arrays.toString(Us));
    }

    public List<List<Item>> findAllMaximalCompatibleItemSets(
            List<Item> items,
            BitSet[] conflictMatrix) {
        int n = items.size();

        // 构造兼容图
        BitSet[] compat = new BitSet[n];
        for (int i = 0; i < n; i++) {
            compat[i] = new BitSet(n);
            compat[i].set(0, n);
            compat[i].clear(i);
            compat[i].andNot(conflictMatrix[i]);
        }

        List<List<Item>> result = new ArrayList<>();

        BitSet R = new BitSet(n);
        BitSet P = new BitSet(n);
        BitSet X = new BitSet(n);

        P.set(0, n);

        bronKerbosch(items, compat, R, P, X, result);

        return result;
    }

    private void bronKerbosch(List<Item> items, BitSet[] compat, BitSet R, BitSet P, BitSet X, List<List<Item>> result) {
        if (P.isEmpty() && X.isEmpty()) {
            result.add(bitSetToItems(R, items));
            return;
        }

        int pivot = choosePivot(compat, P, X);

        BitSet candidates = (BitSet) P.clone();
        if (pivot != -1) {
            candidates.andNot(compat[pivot]);
        }

        for (int v = candidates.nextSetBit(0); v >= 0; v = candidates.nextSetBit(v + 1)) {

            BitSet newR = (BitSet) R.clone();
            newR.set(v);

            BitSet newP = (BitSet) P.clone();
            newP.and(compat[v]);

            BitSet newX = (BitSet) X.clone();
            newX.and(compat[v]);

            bronKerbosch(items, compat, newR, newP, newX, result);

            P.clear(v);
            X.set(v);
        }
    }

    private int choosePivot(BitSet[] compat, BitSet P, BitSet X) {

        BitSet union = (BitSet) P.clone();
        union.or(X);

        int bestPivot = -1;
        int bestScore = -1;

        for (int u = union.nextSetBit(0); u >= 0; u = union.nextSetBit(u + 1)) {

            BitSet tmp = (BitSet) P.clone();
            tmp.and(compat[u]);

            int score = tmp.cardinality();

            if (score > bestScore) {
                bestScore = score;
                bestPivot = u;
            }
        }

        return bestPivot;
    }

    private List<Item> bitSetToItems(BitSet bitSet, List<Item> items) {

        List<Item> list = new ArrayList<>();

        for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
            list.add(items.get(i));
        }

        return list;
    }


    private void solveRootNode() throws IloException {
        rootNode = new Node();
        rootNode.lb = bestSolution.LB;
        rootNode.deep = 0;
        rootNode.isRoot = true;
        rootNode.unionFind = new UnionFind(n);
        rootNode.father = rootNode.unionFind.father.clone();
        rootNode.branchConstraintMatrix = new Boolean[n][n];
        rootNode.key = "";
        rootNode.conflictList = new ArrayList<>();

        // 构建冲突矩阵
        conflictMatrix = new BitSet[n];
        for (int i = 0; i < n; i++) conflictMatrix[i] = new BitSet(n);

        int conflictCnt = 0;
        int edge_num = 0;

        List<Item> copyItems = Item.copy(items);
        if (Parameter.isTimeWindowConstraint) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    int tempStart = Math.max(items.get(i).startTime, items.get(j).startTime);
                    int tempEnd = Math.min(items.get(i).endTime, items.get(j).endTime);
                    if (tempStart > tempEnd && rootNode.branchConstraintMatrix[items.get(i).id][items.get(j).id] == null) {
                        int id1 = items.get(i).id, id2 = items.get(j).id;
                        conflictMatrix[id1].set(id2);
                        conflictMatrix[id2].set(id1);
                        rootNode.branchConstraintMatrix[id1][id2] = false;
                        rootNode.branchConstraintMatrix[id2][id1] = false;
                        conflictCnt++;
                    }
                }
            }
        } else {
            conflictMatrix = instance.conflictMatrix;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (conflictMatrix[i].get(j) && rootNode.branchConstraintMatrix[items.get(i).id][items.get(j).id] == null) {
                        int id1 = items.get(i).id, id2 = items.get(j).id;
                        rootNode.branchConstraintMatrix[id1][id2] = false;
                        rootNode.branchConstraintMatrix[id2][id1] = false;
                        conflictCnt++;
                    }
                }
            }
        }

        if (Parameter.isUseGrouping) {
            for (int i = 0; i < n; i++) {
                edge_num += conflictMatrix[i].cardinality();
            }

            double density = (double) edge_num / (n * (n - 1));

            System.out.println("conflict num = " + conflictCnt);
            System.out.println("conflict density = " + density);

            if (density > 0.25 && conflictCnt >= 400_000) {
                compatibilitySet = findAllMaximalCompatibleItemSets(copyItems, conflictMatrix);
            } else {
                compatibilitySet.add(items);
            }
            // 再次进行校验，如果兼容集过大，说明不能使用分团算法
            if (compatibilitySet.size() >= 400_000) {
                compatibilitySet.clear();
                compatibilitySet.add(items);
            }
            System.out.println("compatibilitySet size = " + compatibilitySet.size());
        } else {
            compatibilitySet.add(items);
        }

        Set<String> set = new HashSet<>();
        // 将每个物品单独装箱
        for (int i = 0; i < n; i++) {
            BinType binType = CommonUtil.findBestFitBinType(items.get(i).volume, binTypes);
            Pattern pattern = CommonUtil.generatePatternByPlaceItemList(n, binType, List.of(items.get(i)));
            if (set.add(pattern.key)) {
                rootNode.patternList.add(pattern);
            }
        }

        // 启发式生成初始解
        List<Bin> binList = new ArrayList<>();
        int cost = 0;

        HeuristicAlgorithm heuristicAlgorithm = new HeuristicAlgorithm(conflictMatrix);
        Sol sol1 = new Sol(instance.items.size());
        heuristicAlgorithm.leastCompFirstHeur(instance, sol1, false, null);
        binList = sol1.bins;
        cost = sol1.totalCost;

        if (Parameter.isTimeWindowConstraint) {
            Sol sol2 = new Sol(instance.items.size());
            heuristicAlgorithm.bestFitHeur(instance, sol2, BFType.Min_End_BF1);

            Sol sol3 = new Sol(instance.items.size());
            heuristicAlgorithm.bestFitHeur(instance, sol3, BFType.Max_Start_BF1rev);

            if (sol1.totalCost < sol2.totalCost) {
                binList = sol1.bins;
                cost = sol1.totalCost;
            } else {
                binList = sol2.bins;
                cost = sol2.totalCost;
            }

            if (sol3.totalCost < cost) {
                binList = sol3.bins;
                cost = sol3.totalCost;
            }
        }

        bestSolution.UB = cost;
        bestSolution.bins = binList;
        for (Bin bin : binList) {
            Pattern pattern = CommonUtil.generatePatternByPlaceItemList(n, bin.binType, bin.placedItemList);
            if (set.add(pattern.key)) {
                rootNode.patternList.add(pattern);
            }
        }

        // 计算每种箱子类型的上下界
        Ls = new int[binTypes.size()];
        Us = new int[binTypes.size()];
        Arrays.fill(Ls, Integer.MIN_VALUE);
        Arrays.fill(Us, Integer.MAX_VALUE);
        calculateLbAndUbForBinTypes(Ls, Us, binTypes, items, bestSolution.UB);

        // 判断初始上下界是否相等
        if (bestSolution.UB == bestSolution.LB) {
            bestSolution.isOpt = true;
            return;
        }

        bestSolution.generatedNodeCnt++;
        solveNodeByCG(rootNode);
    }

    private void branchByDfs() throws IloException {
        // 求解根节点
        solveRootNode();

        System.out.println("UB = " + bestSolution.UB);
        System.out.println("LB = " + bestSolution.LB);
        System.out.println("solution status = " + bestSolution.isOpt);
        long rootTime = System.currentTimeMillis() - TimeUtil.startTime;
        System.out.println("root node time = " + rootTime + "ms");
        System.out.println("=======================root node end===============================");

        if (!bestSolution.isOpt && !TimeUtil.isTimeLimit()) {
            if (Parameter.isDiving) {
                diving = true;
                // 不完全搜索
                long incompleteStartTime = System.currentTimeMillis();
                branchAndBoundByDfs(rootNode);
                long incompleteTime = System.currentTimeMillis() - incompleteStartTime;
                bestSolution.divingTime = incompleteTime;
                System.out.println("incomplete search time = " + incompleteTime + "ms");

                System.out.println("incomplete search generated node cnt = " + bestSolution.exploredDivingNodeCnt);
                System.out.println("current UB = " + bestSolution.UB);
                System.out.println("current LB = " + bestSolution.LB);
                System.out.println("current solution status = " + bestSolution.isOpt);
            }

            if (bestSolution.isOpt || TimeUtil.isTimeLimit()) return;

            // 完全搜索
            long completeStartTime = System.currentTimeMillis();
            if (!bestSolution.isOpt && !TimeUtil.isTimeLimit()) {
                diving = false;
                branchAndBoundByDfs(rootNode);
            }
            System.out.println("complete search time = " + (System.currentTimeMillis() - completeStartTime) + "ms");
            System.out.println("incomplete search generated node cnt = " + bestSolution.exploredDivingNodeCnt);
            System.out.println("complete search generated node cnt = " + bestSolution.exploredNodeCnt);
            System.out.println("current UB = " + bestSolution.UB);
            System.out.println("current LB = " + bestSolution.LB);
            System.out.println("current solution status = " + bestSolution.isOpt);
        }
    }

    // 计算箱子花费的可能性组合
    private void calPossibleCombination() {
        int n = binTypes.size();
        int target = possibleCombination.length - 1;
        boolean[][] dp = new boolean[n + 1][target + 1];
        for (int i = 0; i <= n; i++) {
            dp[i][0] = true;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= target; j++) {
                int preIndex = j - binTypes.get(i - 1).cost;
                if (preIndex >= 0) {
                    dp[i][j] = dp[i - 1][j] || dp[i][preIndex];
                } else {
                    dp[i][j] = dp[i - 1][j];
                }
            }
        }
        for (int j = 0; j <= target; j++) {
            possibleCombination[j] = dp[n][j];
        }
    }

    private boolean isMaxClique(List<Integer> clique, List<Item> items) {
        for (int i = 0; i < clique.size(); i++) {
            for (int j = i + 1; j < clique.size(); j++) {
                int id1 = clique.get(i);
                int id2 = clique.get(j);
                int tempStart = Math.max(items.get(id1).startTime, items.get(id2).startTime);
                int tempEnd = Math.min(items.get(id1).endTime, items.get(id2).endTime);
                if (tempStart <= tempEnd) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<Integer> findCliqueByJohnsonHeuristic(List<Item> items, BitSet[] conflictMatrix) {
        int n = items.size();
        if (n == 0) return new ArrayList<>();

        // 如果没有传入 conflictMatrix，则构建
        BitSet[] adj;
        if (conflictMatrix != null) {
            adj = conflictMatrix;
        } else {
            adj = new BitSet[n];
            for (int i = 0; i < n; i++) adj[i] = new BitSet(n);

            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    Item a = items.get(i);
                    Item b = items.get(j);

                    // 不重叠 -> 连边
                    if (!(Math.max(a.startTime, b.startTime) <= Math.min(a.endTime, b.endTime))) {
                        adj[a.id].set(b.id);
                        adj[b.id].set(a.id);
                    }
                }
            }
        }

        // Johnson 贪心找 clique
        BitSet P = new BitSet(n);
        P.set(0, n);

        List<Integer> maxCliqueIdx = new ArrayList<>();

        while (!P.isEmpty()) {
            int bestV = -1;
            int maxDegree = -1;

            for (int v = P.nextSetBit(0); v >= 0; v = P.nextSetBit(v + 1)) {
                BitSet neighborsInP = (BitSet) adj[v].clone();
                neighborsInP.and(P);
                int degree = neighborsInP.cardinality();

                if (degree > maxDegree) {
                    maxDegree = degree;
                    bestV = v;
                }
            }

            if (bestV == -1) break;

            maxCliqueIdx.add(bestV);

            // 收缩候选集
            P.and(adj[bestV]);
        }

        // 返回 id（或 index）
        List<Integer> resultIds = new ArrayList<>();
        for (int idx : maxCliqueIdx) {
            resultIds.add(items.get(idx).id);
        }

        return resultIds;
    }

    private int getSubSum(int remainingCapacity, List<Item> remainingItems) {
        int[] dp = new int[remainingCapacity + 1];
        for (Item item : remainingItems) {
            for (int j = remainingCapacity; j >= item.volume; j--) {
                dp[j] = Math.max(dp[j], dp[j - item.volume] + item.volume);
            }
        }
        return dp[remainingCapacity];
    }

    private int solveLB(List<Integer> maxClique, List<Item> items, List<BinType> binTypes) throws IloException {
        int n = items.size(), m = binTypes.size();
        int lb_value = 0;
        List<BinType> copyBinTypes = BinType.copyList(binTypes);
        copyBinTypes.sort(Comparator.comparingInt(bt -> bt.capacity));
        boolean[] used = new boolean[n];
        for (int id : maxClique) used[id] = true;
        List<Item> remainingItems = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!used[i]) remainingItems.add(items.get(i));
        }

        // 根据第一个能放入的箱子进行分组
        List<List<Integer>> groups = new ArrayList<>(m);
        for (int i = 0; i < m; i++) groups.add(new ArrayList<>());
        Arrays.fill(used, false);
        for (int i = 0; i < m; i++) {
            int capacity = copyBinTypes.get(i).capacity;
            for (int j = 0; j < maxClique.size(); j++) {
                if (!used[j] && items.get(maxClique.get(j)).volume <= capacity) {
                    groups.get(i).add(items.get(maxClique.get(j)).id);
                    used[j] = true;
                }
            }
        }
        // 构建有效容量
        int[][] effective_w = new int[m][m];
        int[] minW = new int[m];
        Arrays.fill(minW, 0);
        for (int i = 0; i < m; i++) {
            if (groups.get(i).isEmpty()) continue;
            int minVolume = Integer.MAX_VALUE;
            for (int id : groups.get(i)) {
                minVolume = Math.min(minVolume, items.get(id).volume);
            }
            minW[i] = minVolume;
        }
        for (int i = 0; i < m; i++) {
            if (minW[i] == 0) continue;
            for (int j = i; j < m; j++) {
                effective_w[i][j] = getSubSum(copyBinTypes.get(j).capacity - minW[i], remainingItems);
            }
        }

        // 每种类型的数量
        int[] typeCount = new int[m];
        for (int i = 0; i < m; i++) {
            if (groups.get(i).isEmpty()) typeCount[i] = 0;
            else typeCount[i] = groups.get(i).size();
        }

        int totalSum = items.stream().mapToInt(Item::getVolume).sum();
        int remainingItemsVolumeSum = remainingItems.stream().mapToInt(Item::getVolume).sum();
        IloCplex cplex = new IloCplex();
        cplex.setOut(null);
        cplex.setWarning(null);
        cplex.setParam(IloCplex.Param.Threads, 1);
        // 每种箱型选择的数量
        IloIntVar[] y_bar = cplex.intVarArray(m, 0, Integer.MAX_VALUE);
        // 冲突团中箱型的分配
        IloIntVar[][] y = new IloIntVar[m][];
        for (int i = 0; i < m; i++) {
            y[i] = cplex.intVarArray(m, 0, Integer.MAX_VALUE);
        }
        IloLinearNumExpr objective = cplex.linearNumExpr();
        for (int j = 0; j < m; j++) {
            objective.addTerm(copyBinTypes.get(j).cost, y_bar[j]);
        }
        for (int h = 0; h < m; h++) {
            for (int j = h; j < m; j++) {
                objective.addTerm(copyBinTypes.get(j).cost, y[h][j]);
            }
        }
        cplex.addMinimize(objective);
        // 团中箱子类型数量约束
        for (int h = 0; h < m; h++) {
            IloLinearNumExpr expr = cplex.linearNumExpr();
            for (int j = h; j < m; j++) {
                expr.addTerm(1, y[h][j]);
            }
            cplex.addEq(expr, typeCount[h]);
        }

        IloLinearNumExpr expr1 = cplex.linearNumExpr();
        for (int j = 0; j < m; j++) {
            expr1.addTerm(copyBinTypes.get(j).capacity, y_bar[j]);
        }
        for (int h = 0; h < m; h++) {
            for (int j = h; j < m; j++) {
                expr1.addTerm(copyBinTypes.get(j).capacity, y[h][j]);
            }
        }
        cplex.addGe(expr1, totalSum);

        IloLinearNumExpr expr2 = cplex.linearNumExpr();
        for (int j = 0; j < m; j++) {
            expr2.addTerm(copyBinTypes.get(j).capacity, y_bar[j]);
        }
        for (int h = 0; h < m; h++) {
            for (int j = h; j < m; j++) {
                expr2.addTerm(effective_w[h][j], y[h][j]);
            }
        }
        cplex.addGe(expr2, remainingItemsVolumeSum);

        if (cplex.solve()) {
            lb_value = CommonUtil.ceilToInt(cplex.getObjValue());
            System.out.println("LB solve status = " + cplex.getStatus());
            System.out.println("LB = " + lb_value);
        }
        cplex.end();
        return lb_value;
    }

    private void init() throws IloException {
        calPossibleCombination();

        List<Integer> maxClique = null;
        if (Parameter.isTimeWindowConstraint) {
            maxClique = findCliqueByJohnsonHeuristic(items, null);
        } else  {
            maxClique = findCliqueByJohnsonHeuristic(items, instance.conflictMatrix);
        }
        bestSolution.LB = solveLB(maxClique, items, binTypes);
        System.out.println("LB0 = " + bestSolution.LB);
    }

    public void solve() throws IloException {
        init();
        branchByDfs();

        if (!TimeUtil.isTimeLimit()) {
            bestSolution.LB = bestSolution.UB;
            bestSolution.isOpt = true;
        }

        if (bestSolution.generatedNodeCnt == 1 && bestSolution.isOpt) {
            bestSolution.root_isOpt = true;
        }
        if (Parameter.isTimeWindowConstraint) {
            bestSolution.gap = (bestSolution.UB - bestSolution.LB) / (double) bestSolution.UB * 100;
        }
        else {
            bestSolution.gap = (bestSolution.UB - bestSolution.LB) / (double) bestSolution.LB * 100;
        }
        bestSolution.totalTime = System.currentTimeMillis() - TimeUtil.startTime;
        System.out.println("======================================================");
        System.out.println("===================Algorithm End======================");
        System.out.println("======================================================");
        System.out.println(bestSolution);
        System.out.println("bin packing isFeasible = " + CommonUtil.isFeasible(bestSolution.bins, n, conflictMatrix));

        if (!CommonUtil.isFeasible(bestSolution.bins, n, conflictMatrix)) {
            throw new RuntimeException("packing is not Feasible");
        }
    }
}

