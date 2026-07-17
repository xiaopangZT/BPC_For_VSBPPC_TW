package com.xiaopangxianren.solver;

import com.xiaopangxianren.classes.Bin;

import java.util.ArrayList;
import java.util.List;

public class Sol {
    public List<Bin> bins = new ArrayList<>();
    public int[] binOf; // 记录物品 i 属于哪个箱子的索引，-1 表示未分配
    public int totalCost = 0;

    public Sol(int itemCount) {
        this.binOf = new int[itemCount];
        java.util.Arrays.fill(binOf, -1);
    }

    public void clear() {
        bins.clear();
        java.util.Arrays.fill(binOf, -1);
        totalCost = 0;
    }

    public void addBin(Bin bin) {
        bins.add(bin);
        totalCost += bin.binType.cost;
    }
}