package com.xiaopangxianren.utils;

import com.xiaopangxianren.classes.Bin;
import com.xiaopangxianren.classes.BinType;
import com.xiaopangxianren.classes.Item;
import com.xiaopangxianren.classes.Pattern;

import java.util.*;

public class CommonUtil {
    public final static double EPS = 1e-9;
    public static int floorToInt(double x) {
        return (int) Math.floor(x + EPS);
    }

    public static int ceilToInt(double x) {
        return (int) Math.ceil(x - EPS);
    }

    public static boolean isInteger(double x) {
        // 计算差值并与 EPS 比较
        return Math.abs(x - Math.round(x)) < EPS;
    }

    public static int compareDouble(double a, double b) {
        double diff = a - b;
        if (diff > EPS) return 1;
        if (diff < EPS) return -1;
        return 0;
    }

    public static long ceilToLong(double x) {
        return (long) Math.ceil(x - EPS);
    }

    public static List<Pattern> Deduplication(List<Pattern> oldPatterns) {
        int n = oldPatterns.size();
        if (n == 0) return Collections.emptyList();

        boolean[] removed = new boolean[n];

        Map<BitSet, Integer> canonical = new HashMap<>(n);

        for (int i = 0; i < n; i++) {
            Pattern p = oldPatterns.get(i);
            BitSet key = p.bitSet;

            Integer bestIndex = canonical.get(key);
            if (bestIndex == null) {
                canonical.put(key, i);
            } else {
                Pattern bestPattern = oldPatterns.get(bestIndex);
                if (p.binType.cost < bestPattern.binType.cost) {
                    removed[bestIndex] = true;
                    canonical.put(key, i);
                } else {
                    removed[i] = true;
                }
            }
        }

        List<Pattern> out = new ArrayList<>(canonical.size());
        for (int i = 0; i < n; i++) {
            if (!removed[i]) {
                out.add(oldPatterns.get(i));
            }
        }
        return out;
    }

    public static  Pattern generatePatternByPlaceItemList(int n, BinType binType, List<Item> placeItemList) {
        Pattern pattern = new Pattern(n);
        // 记录哪些物品被放入
        for (Item placeItem : placeItemList) {
            pattern.bitSet.set(placeItem.id);
        }
        pattern.placeItems = Item.copy(placeItemList);
        pattern.binType = binType;
        pattern.genKey();
        return pattern;
    }

    // 检查当期装箱方案是否可行
    public static boolean isFeasible(List<Bin> binList, int n, BitSet[] conflictMatrix) {
        boolean[] used = new boolean[n];

        for (Bin bin : binList) {
            int totalVolume = bin.placedItemList.stream().mapToInt(Item::getVolume).sum();
            if (totalVolume > bin.binType.capacity) {
                System.out.println("bin capacity infeasible");
                return false;
            }

            // 检查箱内是否存在冲突物品对
            List<Item> placedItems = bin.placedItemList;
            for (int i = 0; i < placedItems.size(); i++) {
                Item a = placedItems.get(i);
                used[a.id] = true;

                for (int j = i + 1; j < placedItems.size(); j++) {
                    Item b = placedItems.get(j);

                    if (conflictMatrix[a.id].get(b.id)) {
                        System.out.println("bin conflict infeasible");
                        System.out.println("conflict items: " + a.id + " and " + b.id);
                        System.out.println(bin);
                        return false;
                    }
                }
            }
        }

        for (int i = 0; i < n; i++) {
            if (!used[i]) {
                System.out.println("item " + i + " is not packed");
                return false;
            }
        }

        return true;
    }

    public static BinType findBestFitBinType(int usingVolume, List<BinType> binTypes) {
        BinType bestBinType = null;
        int bestRemainCapacity = Integer.MAX_VALUE;

        for (BinType binType : binTypes) {
            if (binType.capacity >= usingVolume) {
                int remain = binType.capacity - usingVolume;
                if (remain < bestRemainCapacity) {
                    bestRemainCapacity = remain;
                    bestBinType = binType;
                }
            }
        }

        return bestBinType;
    }

    public static Bin createBinFromPattern(Pattern pattern, BitSet[] conflictMatrix) {
        Bin bin = new Bin();
        bin.binType = pattern.binType;
        bin.remainingCapacity = pattern.binType.capacity;
        for (Item item : pattern.placeItems) {
            if (bin.canPlaceItem(item))
                bin.packItem(item, conflictMatrix);
            else
                throw new RuntimeException("pattern item list error");
        }
        return bin;
    }

}