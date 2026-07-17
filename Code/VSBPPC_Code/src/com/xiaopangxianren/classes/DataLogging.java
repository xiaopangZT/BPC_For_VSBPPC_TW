package com.xiaopangxianren.classes;

import java.util.ArrayList;
import java.util.List;

public class DataLogging {
    public List<String> fileNames;
    public List<Boolean> isOpt, rootEnu_isOpt, ET_Root;
    public List<Double> gap;
    public List<Integer> UB, LB, generatedNodeCnt, exploredNodeCnt, rmpCnt, pricingCnt, exploredDivingNodeCnt, patternEnuCnt, ET_Enum;
    public List<Long> totalTime, rmpTime, pricingTime, divingTime, findPatternEnuTime, patternEnuTime, avgRmpTime,
                    avgPricingTime, avgFindPatternEnuTime, avgPatternEnuTime;

    public DataLogging() {
        fileNames = new ArrayList<>();
        isOpt = new ArrayList<>();
        rootEnu_isOpt = new ArrayList<>();
        generatedNodeCnt = new ArrayList<>();
        exploredNodeCnt = new ArrayList<>();
        rmpCnt = new ArrayList<>();
        pricingCnt = new ArrayList<>();
        exploredDivingNodeCnt = new ArrayList<>();
        patternEnuCnt = new ArrayList<>();
        totalTime = new ArrayList<>();
        rmpTime = new ArrayList<>();
        pricingTime = new ArrayList<>();
        divingTime = new ArrayList<>();
        findPatternEnuTime = new ArrayList<>();
        patternEnuTime = new ArrayList<>();
        avgPricingTime = new ArrayList<>();
        avgRmpTime = new ArrayList<>();
        avgFindPatternEnuTime = new ArrayList<>();
        avgPatternEnuTime = new ArrayList<>();
        UB = new ArrayList<>();
        LB = new ArrayList<>();
        gap = new ArrayList<>();
        ET_Root = new ArrayList<>();
        ET_Enum = new ArrayList<>();
    }

    public void add_data(String fileName, Solution solution) {
        fileNames.add(fileName);
        UB.add(solution.UB);
        LB.add(solution.LB);
        gap.add(solution.gap);
        isOpt.add(solution.isOpt);
        rootEnu_isOpt.add(solution.rootEnu_isOpt);
        generatedNodeCnt.add(solution.generatedNodeCnt);
        exploredNodeCnt.add(solution.exploredNodeCnt);
        rmpCnt.add(solution.rmpCnt);
        pricingCnt.add(solution.pricingCnt);
        exploredDivingNodeCnt.add(solution.exploredDivingNodeCnt);
        patternEnuCnt.add(solution.patternEnuCnt);
        totalTime.add(solution.totalTime);
        rmpTime.add(solution.rmpTime);
        pricingTime.add(solution.pricingTime);
        divingTime.add(solution.divingTime);
        findPatternEnuTime.add(solution.findPatternEnuTime);
        patternEnuTime.add(solution.patternEnuTime);
        avgRmpTime.add(solution.rmpCnt == 0 ? 0L : solution.rmpTime / solution.rmpCnt);
        avgPricingTime.add(solution.pricingCnt == 0 ? 0L : solution.pricingTime / solution.pricingCnt);
        avgFindPatternEnuTime.add(solution.patternEnuCnt == 0 ? 0L : solution.findPatternEnuTime / solution.patternEnuCnt);
        avgPatternEnuTime.add(solution.patternEnuCnt == 0 ? 0L : solution.patternEnuTime / solution.patternEnuCnt);
        ET_Root.add(solution.ET_Root);
        ET_Enum.add(solution.ET_Enum);
    }
}
