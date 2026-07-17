package com.xiaopangxianren.classes;


import java.util.*;

public class Bin {
    public BinType binType;
    public List<Item> placedItemList = new ArrayList<>();
    public int remainingCapacity;
    private Set<Integer> conflictItemsIndex = new HashSet<>();

    public Bin(BinType binType, List<Item> placedItemList, int remainingCapacity, BitSet[] conflictMatrix) {
        this.binType = binType;
        this.remainingCapacity = remainingCapacity;
        if (placedItemList != null) {
            for (Item item : placedItemList) {
                if (canPlaceItem(item))
                    packItem(item, conflictMatrix);
                else
                    throw new RuntimeException("generate placedItemList error");
            }
        }
    }

    public Bin(BinType binType) {
        this.binType = binType;
        this.remainingCapacity = binType.capacity;
    }

    public Bin() {
    }

    public boolean canPlaceItem(Item item) {
        if (remainingCapacity < item.volume)
            return false;
        return !conflictItemsIndex.contains(item.id);
    }

    public void packItem(Item item, BitSet[] conflictMatrix) {
        placedItemList.add(item);
        remainingCapacity -= item.volume;

        // 更新物品冲突
        for (int i = conflictMatrix[item.id].nextSetBit(0); i >= 0;
             i = conflictMatrix[item.id].nextSetBit(i + 1)) {
            conflictItemsIndex.add(i);
        }
    }

    @Override
    public String toString() {
        return "Bin{" +
                "binType=" + binType +
                ", placedItemList=" + placedItemList +
                ", remainingCapacity=" + remainingCapacity +
                '}';
    }
}