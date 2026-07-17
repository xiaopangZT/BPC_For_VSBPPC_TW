package com.xiaopangxianren.classes;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class BinType {
    public int id;
    @Getter
    public int capacity;
    @Getter
    public int cost;

    public int UB;
    public int LB;

    public BinType() {
    }

    public BinType(int id, int capacity, int cost) {
        this.id = id;
        this.capacity = capacity;
        this.cost = cost;
    }

    public BinType copy() {
        return new BinType(id, capacity, cost);
    }

    public static List<BinType> copyList(List<BinType> in) {
        List<BinType> out = new ArrayList<>(in.size());
        for(BinType binType : in) {
            out.add(binType.copy());
        }
        return out;
    }

    @Override
    public String toString() {
        return "BinType{" +
                "id=" + id +
                ", capacity=" + capacity +
                ", cost=" + cost +
                '}';
    }
}
