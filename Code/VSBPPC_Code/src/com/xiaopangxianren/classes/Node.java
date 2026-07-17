package com.xiaopangxianren.classes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

public class Node {
    public boolean isRoot;
    public int deep;
    public int lb;
    public UnionFind unionFind;
    public List<Pattern> patternList = new ArrayList<>();
    public String key;
    public double[] x;
    public Boolean[][] branchConstraintMatrix;
    public List<int[]> bindList = new ArrayList<>();
    public List<int[]> conflictList = new ArrayList<>();
    public int[] father;

    public Node() {
    }

    public Node(boolean isRoot, int deep, int lb, Boolean[][] branchConstraintMatrix, List<int[]> bindList,
                List<int[]> conflictList, List<Pattern> patternList, int[] fatther, UnionFind unionFind, String key) {
        this.isRoot = isRoot;
        this.deep = deep;
        this.lb = lb;
        this.branchConstraintMatrix = branchConstraintMatrix;
        this.bindList = bindList;
        this.conflictList = conflictList;
        this.patternList = patternList;
        this.father = fatther;
        this.unionFind = unionFind;
        this.key = key;
    }

    public Node createLeftChild(int[] bind) {
        List<int[]> copyBindList = new ArrayList<>(bindList);
        copyBindList.add(bind);

        int indexA = bind[0];
        int indexB = bind[1];

        UnionFind copyUnionFind = unionFind.copy();
        // 将索引A和索引B进行合并，一起加入到并查集中
        copyUnionFind.join(indexA, indexB);

        int[] copyFat = copyUnionFind.father.clone();
        for (int i = 0; i < copyFat.length; i++) copyFat[i] = copyUnionFind.find(i);

        Boolean[][] copyBranchConstraintMatrix = new Boolean[branchConstraintMatrix.length][];
        for (int i = 0; i < copyBranchConstraintMatrix.length; i++)
            copyBranchConstraintMatrix[i] = branchConstraintMatrix[i].clone();
        copyBranchConstraintMatrix[indexA][indexB] = true;
        copyBranchConstraintMatrix[indexB][indexA] = copyBranchConstraintMatrix[indexA][indexB];

        List<Pattern> newPatternList = new ArrayList<>(patternList.size());
        for (Pattern pattern : patternList) {
            BitSet used = pattern.bitSet;
            if (used.get(indexA) == used.get(indexB)) {
                newPatternList.add(pattern);
            }
        }

        return new Node(false, deep + 1, lb, copyBranchConstraintMatrix, copyBindList,
                new ArrayList<>(conflictList),  newPatternList, copyFat, copyUnionFind,
                key + "1@" + Arrays.toString(new int[]{indexA, indexB}));
    }

    public Node createRightChild(int[] conflict) {
        List<int[]> copyConflictList = new ArrayList<>(conflictList);
        copyConflictList.add(conflict);

        int indexA = conflict[0];
        int indexB = conflict[1];

        Boolean[][] copyBranchConstraintMatrix = new Boolean[branchConstraintMatrix.length][];
        for (int i = 0; i < copyBranchConstraintMatrix.length; i++)
            copyBranchConstraintMatrix[i] = branchConstraintMatrix[i].clone();
        copyBranchConstraintMatrix[indexA][indexB] = false;
        copyBranchConstraintMatrix[indexB][indexA] = copyBranchConstraintMatrix[indexA][indexB];

        List<Pattern> newPatternList = new ArrayList<>(patternList.size());
        for (Pattern pattern : patternList) {
            BitSet used = pattern.bitSet;
            if (!(used.get(indexA) && used.get(indexB))) newPatternList.add(pattern);
        }


        return new Node(false, deep + 1, lb, copyBranchConstraintMatrix, new ArrayList<>(bindList),
                copyConflictList, newPatternList, father.clone(), unionFind.copy(),
                key + "0@" + Arrays.toString(new int[]{indexA, indexB}));
    }


    @Override
    public String toString() {
        return "Node{" +
                "isRoot=" + isRoot +
                ", deep=" + deep +
                ", lb=" + lb +
                ", key='" + key + '\'' +
                '}';
    }
}
