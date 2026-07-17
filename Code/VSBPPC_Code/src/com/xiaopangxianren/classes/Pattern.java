package com.xiaopangxianren.classes;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class Pattern {
    public int id, index;
    public String key;   // 模式的唯一标识
    public BinType binType;
    public List<Item> placeItems;
    public double xVal;
    public double reducedCost;
    @Getter
    @Setter
    public int UB, LB;
    public BitSet bitSet;

    public Pattern() {
        placeItems = new ArrayList<>();
    }

    public Pattern(int n) {
        placeItems = new ArrayList<>();
        bitSet = new BitSet(n);
    }

    public Pattern(BinType binType, int n, List<Item> placeItems) {
        this.binType = binType;
        this.placeItems = placeItems;
        bitSet = new BitSet(n);
    }

    public void genKey() {
        if (key == null) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(binType.id + "-");

            for (int i = 0; i < bitSet.size(); i++) {
                if (bitSet.get(i)) stringBuilder.append(i).append("@");
            }
            key = stringBuilder.toString();
        }
    }

    @Override
    public String toString() {
        return "Pattern{" +
                "key='" + key + '\'' +
                '}';
    }
}
