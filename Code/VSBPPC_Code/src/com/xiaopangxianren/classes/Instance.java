package com.xiaopangxianren.classes;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class Instance {
	public String name;
	public int type;
	public int optCost;
	public ArrayList<BinType> binTypes;
	public ArrayList<Item> items;
    public BitSet[] conflictMatrix;

    public Instance() {
        binTypes = new ArrayList<>();
        items = new ArrayList<>();
    }

    public Instance(List<BinType> binTypes, List<Item> items) {
        this.binTypes = new ArrayList<>(binTypes);
        this.items = new ArrayList<>(items);
    }

    public Instance(String name, List<BinType> binTypes, List<Item> items,  BitSet[] conflictMatrix) {
        this.binTypes = new ArrayList<>(binTypes);
        this.items = new ArrayList<>(items);
        this.conflictMatrix = conflictMatrix;
        this.name = name;
    }
}
