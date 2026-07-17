package com.xiaopangxianren.classes;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Arrays;

@AllArgsConstructor
@NoArgsConstructor
@ToString
public class SR_Cut {
    public String key;
    public int[] indexs;
    public double coefficient;
    public double dualValue;

    public SR_Cut(int[] indexs, double coefficient) {
        this.indexs = indexs;
        this.coefficient = coefficient;
        key = Arrays.toString(indexs);
    }

}