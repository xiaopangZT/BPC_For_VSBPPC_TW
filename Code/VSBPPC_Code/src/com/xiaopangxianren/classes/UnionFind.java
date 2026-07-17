package com.xiaopangxianren.classes;

public class UnionFind {
        public int[] father; // 父节点

        // 初始化
        public UnionFind(int n){
            // 一般会申请比所需空间大一点的内存
            father = new int[n + 2];
            for(int i = 0; i < n; i++){
                father[i] = i;
            }
        }

        public UnionFind(int[] father) {
            this.father = father;
        }

        // 查找
        public int find(int x){
            if(x == father[x]){
                return x;
            }else {
                return father[x] = find(father[x]);
            }
        }

        // 是否是同一个父节点
        public boolean isSame(int x, int y){
            x = find(x);
            y = find(y);
            return x == y;
        }

        // 加入边x->y，将二者的父节点和为一体
        public void join(int x, int y){
            x = find(x);
            y = find(y);
            if(x == y){
                return;
            }
            father[y] = x;
        }

        public UnionFind copy(){
            return new UnionFind(father.clone());
        }
    }