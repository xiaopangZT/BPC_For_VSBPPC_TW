package com.xiaopangxianren.classes;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class Item implements Comparable<Item> {
    public int id;
    public int real_id;
    public int index;
    public int startTime;
    public int endTime;
    @Getter
    public int volume;
    public double dualVal;
    public double unitVal;

    public Item() {
    }

    public Item(int id, int index, int startTime, int endTime, int volume, double dualVal, double unitVal) {
        this.id = id;
        this.index = index;
        this.startTime = startTime;
        this.endTime = endTime;
        this.volume = volume;
        this.dualVal = dualVal;
        this.unitVal = unitVal;
    }

    public Item(int id, int index, int volume) {
        this.id = id;
        this.index = index;
        this.volume = volume;
    }

    public Item(int id, int real_id, int index, int volume) {
        this.id = id;
        this.real_id = real_id;
        this.index = index;
        this.volume = volume;
    }

    public Item(int id, int index, int volume, int startTime, int endTime) {
        this.id = id;
        this.index = index;
        this.volume = volume;
        this.startTime = startTime;
        this.endTime = endTime;
    }


    public Item copy() {
        return new Item(id, index, startTime, endTime, volume, dualVal, unitVal);
    }

    public static List<Item> copy(List<Item> in) {
        List<Item> out = new ArrayList<>(in.size());
        for (Item item : in)
            out.add(item.copy());
        return out;
    }

    @Override
    public String toString() {
        return "Item{" +
                "id=" + id +
                ", index=" + index +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", volume=" + volume +
                ", dualVal = " + dualVal +
                '}';
    }

    @Override
    public int compareTo(Item other) {
        // 第一优先级：结束时间升序
        int endTimeCompare = Integer.compare(this.endTime, other.endTime);
        if (endTimeCompare != 0) return endTimeCompare;

        // 第二优先级：开始时间升序
        int startTimeCompare = Integer.compare(this.startTime, other.startTime);
        if (startTimeCompare != 0) return startTimeCompare;

        // 第三优先级：体积降序
        return Integer.compare(other.volume, this.volume);
    }
    
}
