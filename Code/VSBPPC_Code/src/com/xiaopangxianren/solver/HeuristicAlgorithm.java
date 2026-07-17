package com.xiaopangxianren.solver;

import com.xiaopangxianren.classes.Bin;
import com.xiaopangxianren.classes.BinType;
import com.xiaopangxianren.classes.Instance;
import com.xiaopangxianren.classes.Item;

import java.util.*;

public class HeuristicAlgorithm {
    private BitSet[] conflictMatrix;

    public HeuristicAlgorithm(BitSet[] conflictMatrix) {
        this.conflictMatrix = conflictMatrix;
    }

    public void leastCompFirstHeur(Instance data, Sol sol, boolean isShuffle, Random random) {
        sol.clear();
        int n = data.items.size();
        int[] nc = new int[n];
        List<Integer> avail = new ArrayList<>();

        int maxW = data.binTypes.stream().mapToInt(BinType::getCapacity).max().orElse(0);

        // 1. 初始化 nc (兼容性计数)
        for (int i = 0; i < n; i++) {
            int nc_i = 0;
            Item itemI = data.items.get(i);
            int slack_max = maxW - itemI.volume;

            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                Item itemJ = data.items.get(j);

                if (itemJ.volume <= slack_max && isNotConflict(itemI, itemJ)) {
                    nc_i++;
                }
            }
            nc[i] = nc_i;
            avail.add(i);
        }

        // 2. 初始排序（辅助用）：按体积降序
        if (!isShuffle) {
            avail.sort((i, j) -> {
                int cmp = Integer.compare(data.items.get(j).volume, data.items.get(i).volume);
                return (cmp != 0) ? cmp : Integer.compare(i, j);
            });
        } else {
            Collections.shuffle(avail, random);
        }

        // 3. 贪心装箱主循环
        while (!avail.isEmpty()) {
            // 选出 nc 最小的物品作为“种子”物品 (Least Compatible First)
            int seedIdx = Collections.min(avail, (i, j) -> {
                if (nc[i] != nc[j]) return Integer.compare(nc[i], nc[j]); // nc 升序
                if (data.items.get(i).volume != data.items.get(j).volume)
                    return Integer.compare(data.items.get(j).volume, data.items.get(i).volume); // 体积降序
                return Integer.compare(i, j);
            });

            Item seedItem = data.items.get(seedIdx);

            Bin bestBinCandidate = null;
            List<Integer> bestPackedIndices = new ArrayList<>();

            // 遍历所有箱子类型，寻找性价比最高的
            for (BinType type : data.binTypes) {
                if (seedItem.volume > type.capacity) continue;

                // 创建一个临时箱子进行模拟
                Bin trialBin = new Bin(type, null, type.capacity, conflictMatrix);
                trialBin.packItem(seedItem, conflictMatrix);
                List<Integer> trialPackedIndices = new ArrayList<>();
                trialPackedIndices.add(seedIdx);

                // 贪心：尝试把其他可用物品也塞进这个箱子
                for (int jIdx : avail) {
                    if (jIdx == seedIdx) continue;
                    Item candidate = data.items.get(jIdx);
                    if (trialBin.canPlaceItem(candidate)) {
                        trialBin.packItem(candidate, conflictMatrix);
                        trialPackedIndices.add(jIdx);
                    }
                }

                // 评估性价比：Cost / (TotalCapacity - Slack) 即单位载重的成本
                // 交叉相乘：Cost1 * Used2 <= Cost2 * Used1
                if (bestBinCandidate == null || isBetter(type, trialBin, bestBinCandidate)) {
                    bestBinCandidate = trialBin;
                    bestPackedIndices = new ArrayList<>(trialPackedIndices);
                }
            }

            // 4. 执行真正的装箱操作
            if (bestBinCandidate != null) {
                sol.addBin(bestBinCandidate);
                int binIdxInSol = sol.bins.size() - 1;

                for (int idx : bestPackedIndices) {
                    sol.binOf[idx] = binIdxInSol;
                    // 更新 nc：因为这些物品被移出了，减少与之兼容物品的计数
                    updateNC(idx, nc, avail, data, maxW);
                }

                // 从可用列表中移除已装载的物品
                avail.removeIf(idx -> sol.binOf[idx] >= 0);
            }
        }
    }

    // 更新 nc 的辅助函数
    private void updateNC(int removedIdx, int[] nc, List<Integer> avail, Instance data, int maxW) {
        Item removedItem = data.items.get(removedIdx);
        for (int i : avail) {
            Item other = data.items.get(i);
            if (other.volume <= (maxW - removedItem.volume) && isNotConflict(removedItem, other)) {
                nc[i]--;
            }
        }
    }

    // 性价比比较：Cost1 / Used1 < Cost2 / Used2
    private boolean isBetter(BinType currentType, Bin currentBin, Bin bestBin) {
        long currentUsed = currentType.capacity - currentBin.remainingCapacity;
        long bestUsed = bestBin.binType.capacity - bestBin.remainingCapacity;
        return (long) currentType.cost * bestUsed <= (long) bestBin.binType.cost * currentUsed;
    }

    // 时间窗交集判断
    private boolean isNotConflict(Item a, Item b) {
        if (conflictMatrix[a.id].get(b.id)) {
            return false;
        }
        return true;
    }

    public void bestFitHeur(Instance data, Sol sol, BFType bfType) {
        sol.clear();
        int n = data.items.size();

        // 1. 确定排序顺序
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);

        order.sort((i, j) -> {
            Item itemI = data.items.get(i);
            Item itemJ = data.items.get(j);

            switch (bfType) {
                case Min_End_BF1:
                    // 结束时间升序 -> 体积降序 -> 开始时间升序 -> ID升序
                    if (itemI.endTime != itemJ.endTime) return Integer.compare(itemI.endTime, itemJ.endTime);
                    if (itemI.volume != itemJ.volume) return Integer.compare(itemJ.volume, itemI.volume);
                    if (itemI.startTime != itemJ.startTime) return Integer.compare(itemI.startTime, itemJ.startTime);
                    return Integer.compare(i, j);

                case Max_Start_BF1rev:
                    // 开始时间降序 -> 体积降序 -> 结束时间降序 -> ID升序
                    if (itemI.startTime != itemJ.startTime) return Integer.compare(itemJ.startTime, itemI.startTime);
                    if (itemI.volume != itemJ.volume) return Integer.compare(itemJ.volume, itemI.volume);
                    if (itemI.endTime != itemJ.endTime) return Integer.compare(itemJ.endTime, itemI.endTime);
                    return Integer.compare(i, j);

                default: // Duration_Asc (else)
                    // 时间窗跨度升序 -> 体积降序 -> ID升序
                    int durI = itemI.endTime - itemI.startTime;
                    int durJ = itemJ.endTime - itemJ.startTime;
                    if (durI != durJ) return Integer.compare(durI, durJ);
                    if (itemI.volume != itemJ.volume) return Integer.compare(itemJ.volume, itemI.volume);
                    return Integer.compare(i, j);
            }
        });

        // 2. 逐个放置物品
        for (int iIdx : order) {
            Item item = data.items.get(iIdx);
            int kBest = -1;
            int slackBest = Integer.MAX_VALUE;

            // 寻找最佳适应箱子 (Best-Fit)
            for (int k = 0; k < sol.bins.size(); k++) {
                Bin bin = sol.bins.get(k);
                if (bin.canPlaceItem(item)) {
                    // Best-Fit 逻辑：选择放置后剩余空间最小的箱子
                    if (bin.remainingCapacity < slackBest) {
                        kBest = k;
                        slackBest = bin.remainingCapacity;
                    }
                }
            }

            // 3. 如果找不到合适的已有箱子，开设新箱子
            if (kBest == -1) {
                // 找到能装下该物品的最小容量箱子索引
                int typeIdx = 0;
                while (typeIdx < data.binTypes.size() && item.volume > data.binTypes.get(typeIdx).capacity) {
                    typeIdx++;
                }

                if (typeIdx >= data.binTypes.size()) {
                    throw new IllegalStateException("Item " + item.id + " is too large for all bin types.");
                }

                // KF 优化策略：如果大箱子的单位容量成本更低或相等，选择更大的箱子
                if (bfType != BFType.Orig_Liu_2021 && bfType != BFType.Orig_Liu_2021_Reversed) {
                    for (int t = typeIdx + 1; t < data.binTypes.size(); t++) {
                        BinType currentT = data.binTypes.get(t);
                        BinType bestT = data.binTypes.get(typeIdx);
                        // C[t] / W[t] <= C[best] / W[best] -> C[t]*W[best] <= C[best]*W[t]
                        if ((long) currentT.cost * bestT.capacity <= (long) bestT.cost * currentT.capacity) {
                            typeIdx = t;
                        }
                    }
                }

                // 创建新箱子并添加
                Bin newBin = new Bin(data.binTypes.get(typeIdx), null, data.binTypes.get(typeIdx).capacity, conflictMatrix);
                sol.addBin(newBin);
                kBest = sol.bins.size() - 1;
            }

            // 4. 放置物品并更新箱子状态
            sol.bins.get(kBest).packItem(item, conflictMatrix);
            sol.binOf[iIdx] = kBest;
        }

        // 5. 后处理优化：尝试降级箱子类型以节省成本
        if (bfType != BFType.Orig_Liu_2021 && bfType != BFType.Orig_Liu_2021_Reversed) {
            for (int k = 0; k < sol.bins.size(); k++) {
                Bin bin = sol.bins.get(k);
                int usedVolume = bin.binType.capacity - bin.remainingCapacity;

                // 尝试寻找更小且更便宜的箱子类型
                for (int t = 0; t < data.binTypes.indexOf(bin.binType); t++) {
                    BinType potentialType = data.binTypes.get(t);
                    if (potentialType.capacity >= usedVolume) {
                        // Update the objective after replacing the bin type.
                        sol.totalCost -= (bin.binType.cost - potentialType.cost);
                        bin.binType = potentialType;
                        bin.remainingCapacity = potentialType.capacity - usedVolume;
                        break; // 找到了最小的可替换类型
                    }
                }
            }
        }
    }
}
