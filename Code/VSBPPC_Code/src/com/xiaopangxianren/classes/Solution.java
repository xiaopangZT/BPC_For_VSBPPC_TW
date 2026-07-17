package com.xiaopangxianren.classes;

import java.util.ArrayList;
import java.util.List;

public class Solution {
    public String className;
    public double gap, deluxingRedRate;
    public short rootFlag;
    public int LB, UB;
    public List<Bin> bins;
    public List<Pattern> patterns;
    public boolean isOpt, root_isOpt, ET_Root, rootEnu_isOpt;
    public int generatedNodeCnt, rootColCnt, exploredNodeCnt, totalSrCnt,
             rootSrCnt, ET_Enum, rmpCnt, pricingCnt, exploredDivingNodeCnt, patternEnuCnt;
    public long totalTime, rmpTime, pricingTime, divingTime, findPatternEnuTime, patternEnuTime, deluxingTime;
    public Solution() {
        bins = new ArrayList<>();
        patterns = new ArrayList<>();
    }

    @Override
    public String toString() {
        return "Solution{" +
                "isOpt=" + isOpt +
                ", totalTime=" + totalTime +
                ", root_isOpt=" + root_isOpt +
                ", ET_Root=" + ET_Root +
                ", rootEnu_isOpt=" + rootEnu_isOpt +
                ", gap=" + gap +
                ", rootFlag=" + rootFlag +
                ", LB=" + LB +
                ", UB=" + UB +
                ", generatedNodeCnt=" + generatedNodeCnt +
                ", rootColCnt=" + rootColCnt +
                ", exploredNodeCnt=" + exploredNodeCnt +
                ", totalSrCnt=" + totalSrCnt +
                ", ET_Enum=" + ET_Enum +
                ", rootSrCnt=" + rootSrCnt +
                ", rmpCnt=" + rmpCnt +
                ", pricingCnt=" + pricingCnt +
                ", exploredDivingNodeCnt=" + exploredDivingNodeCnt +
                ", rmpTime=" + rmpTime +
                ", pricingTime=" + pricingTime +
                ", divingTime=" + divingTime +
                ", patternEnuCnt=" + patternEnuCnt +
                ", findPatternEnu=" + findPatternEnuTime +
                ", patternEnuTime=" + patternEnuTime +
                '}';
    }
}
