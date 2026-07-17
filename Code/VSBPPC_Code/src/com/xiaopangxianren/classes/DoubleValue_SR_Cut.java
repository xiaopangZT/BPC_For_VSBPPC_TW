package com.xiaopangxianren.classes;

import lombok.AllArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@ToString
public class DoubleValue_SR_Cut {
    public int[] indexs;
    public double dualValue;

    public DoubleValue_SR_Cut(DoubleValue_SR_Cut srCut) {
        this.indexs = srCut.indexs.clone();
        this.dualValue = srCut.dualValue;
    }

    public boolean containsIndex(int i) {
        return indexs[0] == i || indexs[1] == i || indexs[2] == i;
    }
}